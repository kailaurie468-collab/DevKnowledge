package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.mapper.EmbeddingUsageMapper;
import com.devknowledge.model.EmbeddingUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmbeddingUsageService {

    private final EmbeddingUsageMapper usageMapper;

    public void recordUsage(UUID userId, UUID configId, int promptTokens) {
        EmbeddingUsage usage = new EmbeddingUsage();
        usage.setId(UUID.randomUUID());
        usage.setUserId(userId);
        usage.setConfigId(configId);
        usage.setPromptTokens(promptTokens);
        usage.setCreatedAt(Instant.now());
        usageMapper.insert(usage);
    }

    public List<AiConfigResponse.TokenUsage> getWeeklyUsage(UUID userId) {
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));
        List<EmbeddingUsage> usages = usageMapper.selectList(
                new LambdaQueryWrapper<EmbeddingUsage>()
                        .eq(EmbeddingUsage::getUserId, userId)
                        .ge(EmbeddingUsage::getCreatedAt, sevenDaysAgo)
                        .orderByAsc(EmbeddingUsage::getCreatedAt));

        Map<String, Long> dailyUsage = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String date = Instant.now().minus(Duration.ofDays(i))
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().toString();
            dailyUsage.put(date, 0L);
        }

        for (EmbeddingUsage u : usages) {
            String date = u.getCreatedAt()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().toString();
            dailyUsage.merge(date, u.getPromptTokens() != null ? u.getPromptTokens().longValue() : 0L, Long::sum);
        }

        return dailyUsage.entrySet().stream()
                .map(e -> new AiConfigResponse.TokenUsage(e.getKey(), e.getValue()))
                .toList();
    }
}
