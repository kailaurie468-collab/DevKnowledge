package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.ActivityRequest;
import com.devknowledge.mapper.UserActivityMapper;
import com.devknowledge.model.UserActivity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

/**
 * 用户行为记录服务
 * 记录用户操作行为（含 5 分钟去重），为推荐引擎提供数据基础
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final UserActivityMapper activityMapper;

    /**
     * 记录用户行为
     * 内置 5 分钟去重：同一 userId + type + keywords 在 5 分钟内不重复记录
     */
    public Mono<Void> recordActivity(UUID userId, ActivityRequest req) {
        return Mono.fromCallable(() -> {
            // 5 分钟去重检查
            Instant fiveMinAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
            LambdaQueryWrapper<UserActivity> dedupeWrapper = new LambdaQueryWrapper<UserActivity>()
                    .eq(UserActivity::getUserId, userId)
                    .eq(UserActivity::getType, req.getType())
                    .ge(UserActivity::getCreatedAt, fiveMinAgo)
                    .last("LIMIT 1");

            // 如果有关键词，进一步精确匹配
            if (req.getKeywords() != null && req.getKeywords().length > 0) {
                // 检查最近 5 分钟是否有相同 type 的记录
                Long existCount = activityMapper.selectCount(dedupeWrapper);
                if (existCount > 0) {
                    log.debug("行为记录去重：userId={}, type={}", userId, req.getType());
                    return null;
                }
            } else {
                Long existCount = activityMapper.selectCount(dedupeWrapper);
                if (existCount > 0) {
                    log.debug("行为记录去重：userId={}, type={}", userId, req.getType());
                    return null;
                }
            }

            // 写入行为记录
            UserActivity activity = new UserActivity();
            activity.setId(UUID.randomUUID());
            activity.setUserId(userId);
            activity.setType(req.getType());
            activity.setFramework(req.getFramework());
            activity.setKeywords(req.getKeywords());
            activity.setLanguage(req.getLanguage());
            activity.setResultCount(req.getResultCount());
            activity.setMetadata(req.getMetadata());
            activity.setCreatedAt(Instant.now());
            activityMapper.insert(activity);

            log.info("行为记录已保存: userId={}, type={}", userId, req.getType());
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 分页查询用户行为记录（按时间倒序）
     */
    public Mono<java.util.List<UserActivity>> getUserActivities(UUID userId, int page, int size) {
        return Mono.fromCallable(() -> {
            return activityMapper.selectList(
                    new LambdaQueryWrapper<UserActivity>()
                            .eq(UserActivity::getUserId, userId)
                            .orderByDesc(UserActivity::getCreatedAt)
                            .last("LIMIT " + size + " OFFSET " + (page - 1) * size));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 清理过期行为记录
     *
     * @param userId   用户 ID
     * @param keepDays 保留天数
     * @return 删除的记录数
     */
    public Mono<Integer> cleanup(UUID userId, int keepDays) {
        return Mono.fromCallable(() -> {
            Instant cutoff = Instant.now().minus(keepDays, ChronoUnit.DAYS);
            return activityMapper.delete(
                    new LambdaQueryWrapper<UserActivity>()
                            .eq(UserActivity::getUserId, userId)
                            .lt(UserActivity::getCreatedAt, cutoff));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
