package com.quiz.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * COLD PATH - Final results persisted after game ends.
 * Written ONCE when quiz finishes (async).
 */
@Entity
@Table(name = "game_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long quizId;
    private String playerName;
    private int totalScore;
    private int correctAnswers;
    private int totalQuestions;
    private int rank;
    private long avgResponseTimeMs;
    private LocalDateTime completedAt = LocalDateTime.now();
}
