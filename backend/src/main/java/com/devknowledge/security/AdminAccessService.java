package com.devknowledge.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 开发者后台访问控制。
 * 先使用配置的邮箱白名单，避免为普通用户暴露后台入口；后续可迁移到角色表。
 */
@Component
public class AdminAccessService {

    private final JwtTokenProvider jwtTokenProvider;
    private final Set<String> adminEmails;

    public AdminAccessService(
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.admin.emails:}") String configuredEmails) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminEmails = Arrays.stream(configuredEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String authorization) {
        if (adminEmails.isEmpty()
                || authorization == null
                || !authorization.startsWith("Bearer ")) {
            return false;
        }
        try {
            String email = jwtTokenProvider.getEmail(authorization.substring(7));
            return email != null && isAdminEmail(email);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 按邮箱判断是否管理员（profile 接口用它给前端渲染后台入口） */
    public boolean isAdminEmail(String email) {
        return !adminEmails.isEmpty()
                && email != null
                && adminEmails.contains(email.toLowerCase(Locale.ROOT));
    }
}
