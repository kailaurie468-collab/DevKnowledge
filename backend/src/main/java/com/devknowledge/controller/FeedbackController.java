package com.devknowledge.controller;

import com.devknowledge.dto.FeedbackRequest;
import com.devknowledge.model.UserFeedback;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.RequestObservabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户意见反馈接口。
 * 允许未登录用户提交，用户不需要知道内部开发者后台的存在。
 */
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final RequestObservabilityService observabilityService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public Mono<ResponseEntity<Void>> submit(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Request-Id", required = false) String requestHeaderId,
            @Valid @RequestBody FeedbackRequest request) {
        UserFeedback feedback = new UserFeedback();
        feedback.setId(UUID.randomUUID());
        feedback.setRequestId(request.getRequestId() != null ? request.getRequestId() : requestHeaderId);
        feedback.setUserId(extractUserId(authHeader));
        feedback.setFeedbackType(request.getFeedbackType());
        feedback.setContent(request.getContent());
        feedback.setContact(request.getContact());
        feedback.setPage(request.getPage());
        feedback.setStatus("NEW");
        feedback.setCreatedAt(Instant.now());

        return observabilityService.submitFeedback(feedback)
                .thenReturn(ResponseEntity.accepted().build());
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtTokenProvider.getUserId(authHeader.substring(7));
        } catch (Exception ignored) {
            return null;
        }
    }
}
