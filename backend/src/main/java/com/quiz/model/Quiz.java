package com.quiz.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * COLD PATH - Quiz metadata persisted in DB.
 */
@Entity
@Table(name = "quizzes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String hostName;

    private String joinCode; // 6-char code for players to join

    @Enumerated(EnumType.STRING)
    private QuizStatus status = QuizStatus.WAITING;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum QuizStatus {
        WAITING, ACTIVE, FINISHED
    }
}
