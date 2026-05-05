package com.quiz.service;

import com.quiz.model.GameResult;
import com.quiz.model.Question;
import com.quiz.model.Quiz;
import com.quiz.model.dto.QuizDTO;
import com.quiz.repository.GameResultRepository;
import com.quiz.repository.QuestionRepository;
import com.quiz.repository.QuizRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * ██  GAME ENGINE - Orchestrates hot path + cold path  ██
 * 
 * Hot path: All WebSocket operations (answer processing, leaderboard, timer)
 * Cold path: Quiz creation, question loading (startup), result persistence (end)
 */
@Service
public class GameEngineService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private GameResultRepository gameResultRepository;

    // ██ ACTIVE SESSIONS - The hot path store ██
    private final ConcurrentHashMap<String, GameSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledExecutorService> sessionTimers = new ConcurrentHashMap<>();

    // =============================================
    //  COLD PATH: Session Creation (DB reads)
    // =============================================

    /**
     * Create a game session: reads from DB ONCE, then everything is in-memory.
     */
    public GameSession createSession(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz not found: " + quizId));

        List<Question> dbQuestions = questionRepository.findByQuizIdOrderById(quizId);
        if (dbQuestions.isEmpty()) {
            throw new RuntimeException("No questions found for quiz: " + quizId);
        }

        String sessionId = quiz.getJoinCode();
        GameSession session = new GameSession(sessionId, quizId, quiz.getTitle());

        // Load questions into memory cache - THIS IS THE LAST DB READ
        List<GameSession.QuestionCache> cached = new ArrayList<>();
        for (Question q : dbQuestions) {
            GameSession.QuestionCache qc = new GameSession.QuestionCache();
            qc.setId(q.getId());
            qc.setQuestionText(q.getQuestionText());
            qc.setOptionA(q.getOptionA());
            qc.setOptionB(q.getOptionB());
            qc.setOptionC(q.getOptionC());
            qc.setOptionD(q.getOptionD());
            qc.setCorrectOption(q.getCorrectOption());
            qc.setTimeLimit(q.getTimeLimit());
            qc.setPoints(q.getPoints());
            qc.setCategory(q.getCategory());
            qc.setDifficulty(q.getDifficulty());
            cached.add(qc);
        }
        session.setQuestions(cached);

        activeSessions.put(sessionId, session);

        // Update quiz status in DB
        quiz.setStatus(Quiz.QuizStatus.ACTIVE);
        quizRepository.save(quiz);

        return session;
    }

    public GameSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public GameSession getOrCreateSession(String joinCode) {
        GameSession existing = activeSessions.get(joinCode);
        if (existing != null) return existing;

        Quiz quiz = quizRepository.findByJoinCode(joinCode)
                .orElseThrow(() -> new RuntimeException("Invalid join code: " + joinCode));
        return createSession(quiz.getId());
    }

    // =============================================
    //  HOT PATH: Player Join (in-memory only)
    // =============================================

    public QuizDTO.PlayerJoined joinPlayer(String sessionId, String playerName, String avatarUrl) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null) throw new RuntimeException("Session not found: " + sessionId);

        if (session.hasPlayer(playerName)) {
            // Allow rejoin - return current state
            return new QuizDTO.PlayerJoined(playerName, session.getPlayerCount(), session.getPlayerNames(), avatarUrl);
        }

        session.addPlayer(playerName, avatarUrl);

        return new QuizDTO.PlayerJoined(
                playerName,
                session.getPlayerCount(),
                session.getPlayerNames(),
                avatarUrl
        );
    }

    // =============================================
    //  HOT PATH: Answer Processing (ZERO DB)
    // =============================================

    public QuizDTO.AnswerResult processAnswer(String sessionId, QuizDTO.AnswerSubmission submission) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null || session.getPhase() != GameSession.GamePhase.QUESTION) {
            return new QuizDTO.AnswerResult(false, "", 0, 0, 0, 0, 0);
        }

        QuizDTO.AnswerResult result = session.processAnswer(
                submission.getPlayerName(),
                submission.getQuestionIndex(),
                submission.getSelectedOption(),
                submission.getClientTimestamp()
        );

        // Broadcast updated leaderboard to all (hot path)
        broadcastLeaderboard(sessionId);

        // If all players answered, auto-advance
        if (session.allAnswered()) {
            revealAnswer(sessionId);
        }

        return result;
    }

    // =============================================
    //  HOT PATH: Game Flow Control
    // =============================================

    public void startGame(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null) return;

        // Start first question
        advanceToNextQuestion(sessionId);
    }

    public void advanceToNextQuestion(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null) return;

        int nextIndex = session.getCurrentQuestionIndex() + 1;

        if (nextIndex >= session.getQuestions().size()) {
            endGame(sessionId);
            return;
        }

        session.startQuestion(nextIndex);

        // Broadcast question to all players
        QuizDTO.QuestionBroadcast qb = session.getCurrentQuestionBroadcast();
        messagingTemplate.convertAndSend("/topic/quiz/" + sessionId + "/question", qb);

        // Broadcast game state
        broadcastGameState(sessionId);

        // Start countdown timer
        startTimer(sessionId, session.getQuestions().get(nextIndex).getTimeLimit());
    }

    public void revealAnswer(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null) return;

        // Cancel timer
        cancelTimer(sessionId);

        session.setPhase(GameSession.GamePhase.ANSWER_REVEAL);

        // Broadcast question stats (answer distribution)
        QuizDTO.QuestionStats stats = session.getQuestionStats();
        messagingTemplate.convertAndSend("/topic/quiz/" + sessionId + "/stats", stats);

        // Broadcast game state
        broadcastGameState(sessionId);

        // Auto-advance to leaderboard after 3 seconds
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.schedule(() -> showLeaderboard(sessionId), 3, TimeUnit.SECONDS);
    }

    public void showLeaderboard(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null) return;

        session.setPhase(GameSession.GamePhase.LEADERBOARD);
        broadcastLeaderboard(sessionId);
        broadcastGameState(sessionId);
    }

    private void broadcastLeaderboard(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null) return;

        QuizDTO.LeaderboardUpdate leaderboard = session.getLeaderboard();
        messagingTemplate.convertAndSend("/topic/quiz/" + sessionId + "/leaderboard", leaderboard);
    }

    private void broadcastGameState(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null) return;

        QuizDTO.GameState state = session.getGameState();
        messagingTemplate.convertAndSend("/topic/quiz/" + sessionId + "/state", state);
    }

    // =============================================
    //  TIMER: In-memory countdown
    // =============================================

    private void startTimer(String sessionId, int seconds) {
        cancelTimer(sessionId);

        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        sessionTimers.put(sessionId, timer);

        GameSession session = activeSessions.get(sessionId);

        timer.scheduleAtFixedRate(new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    cancelTimer(sessionId);
                    // Time's up - auto reveal
                    revealAnswer(sessionId);
                    return;
                }

                session.setTimeRemaining(remaining);

                // Broadcast timer tick
                QuizDTO.TimerTick tick = new QuizDTO.TimerTick(remaining, seconds, session.getCurrentQuestionIndex());
                messagingTemplate.convertAndSend("/topic/quiz/" + sessionId + "/timer", tick);

                remaining--;
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void cancelTimer(String sessionId) {
        ScheduledExecutorService timer = sessionTimers.remove(sessionId);
        if (timer != null) {
            timer.shutdownNow();
        }
    }

    // =============================================
    //  COLD PATH: End Game + Persist Results (Async)
    // =============================================

    public void endGame(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null) return;

        cancelTimer(sessionId);
        session.setPhase(GameSession.GamePhase.FINISHED);

        broadcastLeaderboard(sessionId);
        broadcastGameState(sessionId);

        // Async persist to DB - does NOT block the WebSocket
        persistResultsAsync(session);
    }

    @Async
    public void persistResultsAsync(GameSession session) {
        try {
            QuizDTO.LeaderboardUpdate leaderboard = session.getLeaderboard();
            List<GameResult> results = new ArrayList<>();

            for (QuizDTO.LeaderboardEntry entry : leaderboard.getEntries()) {
                GameSession.PlayerState ps = session.getPlayers().get(entry.getPlayerName());
                GameResult result = new GameResult();
                result.setQuizId(session.getQuizId());
                result.setPlayerName(entry.getPlayerName());
                result.setTotalScore(entry.getScore());
                result.setCorrectAnswers(entry.getCorrectAnswers());
                result.setTotalQuestions(session.getQuestions().size());
                result.setRank(entry.getRank());
                result.setAvgResponseTimeMs(ps.getAnsweredCount() > 0 ? ps.getTotalResponseTimeMs() / ps.getAnsweredCount() : 0);
                results.add(result);
            }

            gameResultRepository.saveAll(results);

            // Update quiz status
            Quiz quiz = quizRepository.findById(session.getQuizId()).orElse(null);
            if (quiz != null) {
                quiz.setStatus(Quiz.QuizStatus.FINISHED);
                quizRepository.save(quiz);
            }

            // Clean up session after persistence
            activeSessions.remove(session.getSessionId());
        } catch (Exception e) {
            System.err.println("Failed to persist results: " + e.getMessage());
        }
    }

    // =============================================
    //  UTILITY
    // =============================================

    public Collection<GameSession> getAllActiveSessions() {
        return activeSessions.values();
    }

    public boolean sessionExists(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
}
