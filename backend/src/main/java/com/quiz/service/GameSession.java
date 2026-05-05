package com.quiz.service;

import com.quiz.model.dto.QuizDTO;
import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * ██  HOT PATH - PURE IN-MEMORY  ██
 * 
 * This is the CORE real-time data structure.
 * ZERO database calls. All operations O(1) or O(n log n) for sorting.
 * 
 * One GameSession per active quiz. Destroyed after game ends + results persisted.
 */
@Data
public class GameSession {

    private final String sessionId;
    private final Long quizId;
    private final String quizTitle;

    // --- In-memory question cache (loaded from DB ONCE at start) ---
    private List<QuestionCache> questions = new ArrayList<>();

    // --- Player state: ConcurrentHashMap for thread safety ---
    private final ConcurrentHashMap<String, PlayerState> players = new ConcurrentHashMap<>();

    // --- Game phase management ---
    private volatile GamePhase phase = GamePhase.WAITING;
    private volatile int currentQuestionIndex = -1;
    private volatile int timeRemaining = 0;
    private volatile long questionStartTime = 0;

    // --- Answer tracking per question ---
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String, AnswerRecord>> answersByQuestion = new ConcurrentHashMap<>();

    public enum GamePhase {
        WAITING, QUESTION, ANSWER_REVEAL, LEADERBOARD, FINISHED
    }

    // --- Cached question (from DB, stored in memory) ---
    @Data
    public static class QuestionCache {
        private Long id;
        private String questionText;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String correctOption;
        private int timeLimit;
        private int points;
        private String category;
        private String difficulty;
    }

    // --- Per-player in-memory state ---
    @Data
    public static class PlayerState {
        private String playerName;
        private int score = 0;
        private int correctAnswers = 0;
        private int streak = 0;
        private int maxStreak = 0;
        private long totalResponseTimeMs = 0;
        private int answeredCount = 0;
        private String avatarUrl;
        private long joinedAt = System.currentTimeMillis();
    }

    // --- Single answer record ---
    @Data
    public static class AnswerRecord {
        private String playerName;
        private String selectedOption;
        private long responseTimeMs;
        private boolean correct;
        private int pointsEarned;
        private long serverTimestamp = System.currentTimeMillis();
    }

    // =============================================
    //  HOT PATH OPERATIONS - NO DB CALLS
    // =============================================

    public void addPlayer(String playerName, String avatarUrl) {
        PlayerState state = new PlayerState();
        state.setPlayerName(playerName);
        state.setAvatarUrl(avatarUrl != null ? avatarUrl : "");
        players.put(playerName, state);
    }

    public boolean hasPlayer(String playerName) {
        return players.containsKey(playerName);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public List<String> getPlayerNames() {
        return new ArrayList<>(players.keySet());
    }

    /**
     * CRITICAL HOT PATH: Process answer submission.
     * Pure in-memory. O(1) per answer.
     * Returns points earned (0 if wrong or duplicate).
     */
    public QuizDTO.AnswerResult processAnswer(String playerName, int questionIndex, String selectedOption, long clientTimestamp) {
        PlayerState player = players.get(playerName);
        if (player == null || questionIndex != currentQuestionIndex) {
            return new QuizDTO.AnswerResult(false, "", 0, 0, 0, 0, 0);
        }

        // Ensure answer map exists for this question
        answersByQuestion.putIfAbsent(questionIndex, new ConcurrentHashMap<>());
        ConcurrentHashMap<String, AnswerRecord> questionAnswers = answersByQuestion.get(questionIndex);

        // Prevent duplicate answers
        if (questionAnswers.containsKey(playerName)) {
            return new QuizDTO.AnswerResult(false, "", 0, player.getScore(), 0, getRank(playerName), player.getStreak());
        }

        QuestionCache question = questions.get(questionIndex);
        long responseTimeMs = System.currentTimeMillis() - questionStartTime;
        boolean isCorrect = selectedOption.equalsIgnoreCase(question.getCorrectOption());

        // Calculate points with time bonus
        int pointsEarned = 0;
        if (isCorrect) {
            double timeRatio = Math.max(0, 1.0 - (double) responseTimeMs / (question.getTimeLimit() * 1000));
            int timeBonus = (int) (question.getPoints() * 0.5 * timeRatio);
            pointsEarned = question.getPoints() + timeBonus;

            player.setScore(player.getScore() + pointsEarned);
            player.setCorrectAnswers(player.getCorrectAnswers() + 1);
            player.setStreak(player.getStreak() + 1);
            player.setMaxStreak(Math.max(player.getMaxStreak(), player.getStreak()));
        } else {
            player.setStreak(0);
        }

        player.setTotalResponseTimeMs(player.getTotalResponseTimeMs() + responseTimeMs);
        player.setAnsweredCount(player.getAnsweredCount() + 1);

        // Record answer
        AnswerRecord record = new AnswerRecord();
        record.setPlayerName(playerName);
        record.setSelectedOption(selectedOption);
        record.setResponseTimeMs(responseTimeMs);
        record.setCorrect(isCorrect);
        record.setPointsEarned(pointsEarned);
        questionAnswers.put(playerName, record);

        return new QuizDTO.AnswerResult(
                isCorrect,
                "", // Don't reveal correct answer until reveal phase
                pointsEarned,
                player.getScore(),
                responseTimeMs,
                getRank(playerName),
                player.getStreak()
        );
    }

    /**
     * Get current rank for a player. O(n) but n is small (players).
     */
    public int getRank(String playerName) {
        PlayerState target = players.get(playerName);
        if (target == null) return 0;

        int rank = 1;
        for (PlayerState p : players.values()) {
            if (p.getScore() > target.getScore()) rank++;
        }
        return rank;
    }

    /**
     * Generate sorted leaderboard. O(n log n).
     */
    public QuizDTO.LeaderboardUpdate getLeaderboard() {
        List<PlayerState> sorted = players.values().stream()
                .sorted((a, b) -> {
                    if (b.getScore() != a.getScore()) return b.getScore() - a.getScore();
                    return Long.compare(a.getTotalResponseTimeMs(), b.getTotalResponseTimeMs());
                })
                .collect(Collectors.toList());

        List<QuizDTO.LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            PlayerState ps = sorted.get(i);
            entries.add(new QuizDTO.LeaderboardEntry(
                    ps.getPlayerName(),
                    ps.getScore(),
                    i + 1,
                    ps.getCorrectAnswers(),
                    ps.getStreak(),
                    ps.getAvatarUrl()
            ));
        }

        int answeredCount = 0;
        ConcurrentHashMap<String, AnswerRecord> qa = answersByQuestion.get(currentQuestionIndex);
        if (qa != null) answeredCount = qa.size();

        return new QuizDTO.LeaderboardUpdate(
                sessionId,
                currentQuestionIndex,
                entries,
                players.size(),
                answeredCount
        );
    }

    /**
     * Get answer distribution stats for current question.
     */
    public QuizDTO.QuestionStats getQuestionStats() {
        ConcurrentHashMap<String, AnswerRecord> answers = answersByQuestion.getOrDefault(currentQuestionIndex, new ConcurrentHashMap<>());
        QuestionCache question = questions.get(currentQuestionIndex);

        Map<String, Integer> optionCounts = new HashMap<>();
        optionCounts.put("A", 0);
        optionCounts.put("B", 0);
        optionCounts.put("C", 0);
        optionCounts.put("D", 0);

        double totalResponseTime = 0;
        for (AnswerRecord record : answers.values()) {
            optionCounts.merge(record.getSelectedOption().toUpperCase(), 1, Integer::sum);
            totalResponseTime += record.getResponseTimeMs();
        }

        double avgTime = answers.isEmpty() ? 0 : totalResponseTime / answers.size();

        return new QuizDTO.QuestionStats(
                currentQuestionIndex,
                question.getCorrectOption(),
                optionCounts,
                answers.size(),
                players.size(),
                avgTime
        );
    }

    public void startQuestion(int index) {
        this.currentQuestionIndex = index;
        this.phase = GamePhase.QUESTION;
        this.questionStartTime = System.currentTimeMillis();
        this.timeRemaining = questions.get(index).getTimeLimit();
    }

    public QuizDTO.QuestionBroadcast getCurrentQuestionBroadcast() {
        QuestionCache q = questions.get(currentQuestionIndex);
        return new QuizDTO.QuestionBroadcast(
                currentQuestionIndex,
                questions.size(),
                q.getQuestionText(),
                q.getOptionA(),
                q.getOptionB(),
                q.getOptionC(),
                q.getOptionD(),
                q.getTimeLimit(),
                q.getPoints(),
                q.getCategory(),
                q.getDifficulty()
        );
    }

    public QuizDTO.GameState getGameState() {
        return new QuizDTO.GameState(
                sessionId,
                phase.name(),
                currentQuestionIndex,
                questions.size(),
                players.size(),
                timeRemaining,
                quizTitle
        );
    }

    public boolean allAnswered() {
        ConcurrentHashMap<String, AnswerRecord> qa = answersByQuestion.get(currentQuestionIndex);
        return qa != null && qa.size() >= players.size();
    }

    public boolean hasMoreQuestions() {
        return currentQuestionIndex + 1 < questions.size();
    }
}
