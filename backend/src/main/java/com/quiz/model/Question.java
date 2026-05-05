package com.quiz.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * COLD PATH - Persisted in DB.
 * Loaded into memory when quiz session starts.
 */
@Entity
@Table(name = "questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String questionText;

    @Column(nullable = false)
    private String optionA;

    @Column(nullable = false)
    private String optionB;

    @Column(nullable = false)
    private String optionC;

    @Column(nullable = false)
    private String optionD;

    @Column(nullable = false)
    private String correctOption; // "A", "B", "C", "D"

    @Column(nullable = false)
    private int timeLimit; // seconds

    @Column(nullable = false)
    private int points;

    private String category;

    private String difficulty; // EASY, MEDIUM, HARD

    @Column(name = "quiz_id")
    private Long quizId;
}
