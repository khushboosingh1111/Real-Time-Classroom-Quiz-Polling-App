package com.quiz.controller;

import com.quiz.model.dto.QuizDTO;
import com.quiz.service.GameEngineService;
import com.quiz.service.GameSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class QuizWebSocketController {

    @Autowired
    private GameEngineService gameEngine;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/quiz/{sessionId}/join")
    public void joinQuiz(@DestinationVariable String sessionId, QuizDTO.JoinRequest request) {
        System.out.println(">>> RECEIVED JOIN REQUEST for session: " + sessionId + " from: " + request.getPlayerName());
        try {
            GameSession session = gameEngine.getOrCreateSession(sessionId);
            QuizDTO.PlayerJoined joined = gameEngine.joinPlayer(sessionId, request.getPlayerName(), request.getAvatarUrl());
            
            System.out.println(">>> BROADCASTING to /topic/quiz/" + sessionId + "/players");
            messagingTemplate.convertAndSend("/topic/quiz/" + sessionId + "/players", joined);
            messagingTemplate.convertAndSend("/topic/quiz/" + sessionId + "/state", session.getGameState());
        } catch (Exception e) {
            System.err.println("Join error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** CRITICAL HOT PATH - ZERO DB CALLS */
    @MessageMapping("/quiz/{sessionId}/answer")
    public void submitAnswer(@DestinationVariable String sessionId, QuizDTO.AnswerSubmission submission) {
        try {
            submission.setSessionId(sessionId);
            QuizDTO.AnswerResult result = gameEngine.processAnswer(sessionId, submission);
            messagingTemplate.convertAndSend(
                "/topic/quiz/" + sessionId + "/answer/" + submission.getPlayerName(), result);
        } catch (Exception e) {
            System.err.println("Answer error: " + e.getMessage());
        }
    }

    @MessageMapping("/quiz/{sessionId}/start")
    public void startGame(@DestinationVariable String sessionId) {
        System.out.println(">>> RECEIVED START GAME for session: " + sessionId);
        gameEngine.startGame(sessionId);
    }

    @MessageMapping("/quiz/{sessionId}/next")
    public void nextQuestion(@DestinationVariable String sessionId) {
        gameEngine.advanceToNextQuestion(sessionId);
    }

    @MessageMapping("/quiz/{sessionId}/reveal")
    public void revealAnswer(@DestinationVariable String sessionId) {
        gameEngine.revealAnswer(sessionId);
    }

    @MessageMapping("/quiz/{sessionId}/leaderboard")
    public void showLeaderboard(@DestinationVariable String sessionId) {
        gameEngine.showLeaderboard(sessionId);
    }
}
