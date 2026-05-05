package com.quiz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * HOT PATH DTOs - All WebSocket message payloads.
 * Zero DB dependency. Pure in-memory structures.
 */
public class QuizDTO {

    // --- Player sends this to join ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinRequest {
        private String playerName;
        private String joinCode;
        private String avatarUrl;
    }

    // --- Player sends this to answer ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerSubmission {
        private String playerName;
        private String sessionId;
        private int questionIndex;
        private String selectedOption; // "A","B","C","D"
        private long clientTimestamp;  // for latency compensation
    }

    // --- Server sends question to all ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionBroadcast {
        private int questionIndex;
        private int totalQuestions;
        private String questionText;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private int timeLimit;
        private int points;
        private String category;
        private String difficulty;
    }

    // --- Server sends answer result to individual player ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerResult {
        private boolean correct;
        private String correctOption;
        private int pointsEarned;
        private int totalScore;
        private long responseTimeMs;
        private int currentRank;
        private int streak;
    }

    // --- Leaderboard entry ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderboardEntry {
        private String playerName;
        private int score;
        private int rank;
        private int correctAnswers;
        private int streak;
        private String avatarUrl;
    }

    // --- Full leaderboard broadcast ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderboardUpdate {
        private String sessionId;
        private int questionIndex;
        private List<LeaderboardEntry> entries;
        private int totalPlayers;
        private int answeredCount;
    }

    // --- Game state broadcast ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameState {
        private String sessionId;
        private String phase; // WAITING, QUESTION, ANSWER_REVEAL, LEADERBOARD, FINISHED
        private int currentQuestion;
        private int totalQuestions;
        private int playerCount;
        private int timeRemaining;
        private String quizTitle;
    }

    // --- Player joined notification ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerJoined {
        private String playerName;
        private int playerCount;
        private List<String> allPlayers;
        private String avatarUrl;
    }

    // --- Timer tick ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimerTick {
        private int remaining;
        private int total;
        private int questionIndex;
    }

    // --- Question stats after reveal ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionStats {
        private int questionIndex;
        private String correctOption;
        private Map<String, Integer> optionCounts; // A->5, B->12, etc.
        private int totalAnswered;
        private int totalPlayers;
        private double avgResponseTimeMs;
    }
}
