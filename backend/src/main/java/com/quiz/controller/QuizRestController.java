package com.quiz.controller;

import com.quiz.model.Quiz;
import com.quiz.model.GameResult;
import com.quiz.repository.GameResultRepository;
import com.quiz.repository.QuizRepository;
import com.quiz.service.GameEngineService;
import com.quiz.service.GameSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class QuizRestController {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private GameResultRepository gameResultRepository;

    @Autowired
    private GameEngineService gameEngine;

    /** Get all available quizzes (cold path) */
    @GetMapping("/quizzes")
    public ResponseEntity<List<Quiz>> getAllQuizzes() {
        return ResponseEntity.ok(quizRepository.findAll());
    }

    /** Get quiz by join code */
    @GetMapping("/quizzes/join/{code}")
    public ResponseEntity<?> getQuizByCode(@PathVariable String code) {
        return quizRepository.findByJoinCode(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Create session for a quiz (cold path - one time) */
    @PostMapping("/quizzes/{quizId}/session")
    public ResponseEntity<?> createSession(@PathVariable Long quizId) {
        try {
            GameSession session = gameEngine.createSession(quizId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("sessionId", session.getSessionId());
            resp.put("quizTitle", session.getQuizTitle());
            resp.put("totalQuestions", session.getQuestions().size());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Get active session info (hot path - in memory) */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSessionInfo(@PathVariable String sessionId) {
        GameSession session = gameEngine.getSession(sessionId);
        if (session == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(session.getGameState());
    }

    /** Get historical results (cold path) */
    @GetMapping("/results/{quizId}")
    public ResponseEntity<List<GameResult>> getResults(@PathVariable Long quizId) {
        return ResponseEntity.ok(gameResultRepository.findByQuizIdOrderByRankAsc(quizId));
    }

    /** Health check */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("activeSessions", gameEngine.getAllActiveSessions().size());
        status.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(status);
    }
}
