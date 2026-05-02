package com.devknowledge.controller;

import com.devknowledge.dto.*;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/auth/register")
    public Mono<ResponseEntity<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/auth/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/auth/refresh")
    public Mono<ResponseEntity<AuthResponse>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return authService.refresh(refreshToken)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/user/profile")
    public Mono<ResponseEntity<UserResponse>> getProfile(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        UUID userId = jwtTokenProvider.getUserId(token);
        return authService.getProfile(userId)
                .map(ResponseEntity::ok);
    }
}
