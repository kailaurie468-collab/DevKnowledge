package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.AuthResponse;
import com.devknowledge.dto.LoginRequest;
import com.devknowledge.dto.RegisterRequest;
import com.devknowledge.dto.UserResponse;
import com.devknowledge.mapper.UserMapper;
import com.devknowledge.model.User;
import com.devknowledge.security.AdminAccessService;
import com.devknowledge.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AdminAccessService adminAccessService;

    public Mono<AuthResponse> register(RegisterRequest req) {
        return Mono.fromCallable(() -> {
            // 检查邮箱是否已存在
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
            if (count > 0) {
                throw new RuntimeException("邮箱已被注册");
            }

            User user = new User();
            user.setId(UUID.randomUUID());
            user.setEmail(req.getEmail());
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            user.setDisplayName(req.getDisplayName());
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            userMapper.insert(user);

            return buildAuthResponse(user);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AuthResponse> login(LoginRequest req) {
        return Mono.fromCallable(() -> {
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
            if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
                throw new RuntimeException("邮箱或密码错误");
            }
            return buildAuthResponse(user);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AuthResponse> refresh(String refreshToken) {
        return Mono.fromCallable(() -> {
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                throw new RuntimeException("Refresh token 无效或已过期");
            }
            UUID userId = jwtTokenProvider.getUserId(refreshToken);
            String email = jwtTokenProvider.getEmail(refreshToken);
            return new AuthResponse(
                    jwtTokenProvider.generateAccessToken(userId, email),
                    jwtTokenProvider.generateRefreshToken(userId, email),
                    jwtTokenProvider.getAccessTokenExpiration()
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<UserResponse> getProfile(UUID userId) {
        return Mono.fromCallable(() -> {
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }
            UserResponse resp = new UserResponse();
            resp.setId(user.getId());
            resp.setEmail(user.getEmail());
            resp.setDisplayName(user.getDisplayName());
            // 邮箱白名单判定，前端据此渲染后台入口
            resp.setAdmin(adminAccessService.isAdminEmail(user.getEmail()));
            return resp;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());
        return new AuthResponse(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }
}
