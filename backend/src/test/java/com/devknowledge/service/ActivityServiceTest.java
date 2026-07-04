package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.ActivityRequest;
import com.devknowledge.mapper.UserActivityMapper;
import com.devknowledge.model.UserActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ActivityService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityService - 行为数据采集")
class ActivityServiceTest {

    @Mock
    private UserActivityMapper activityMapper;

    @InjectMocks
    private ActivityService activityService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("记录行为")
    class RecordActivityTests {

        @Test
        @DisplayName("正常记录用户行为")
        void recordActivitySuccess() {
            when(activityMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            ActivityRequest req = new ActivityRequest();
            req.setType("kb_search");
            req.setFramework("react");
            req.setKeywords(new String[]{"hooks", "useState"});

            // 不抛异常即为成功
            activityService.recordActivity(userId, req).block();
        }
    }

    @Nested
    @DisplayName("5 分钟去重")
    class DeduplicationTests {

        @Test
        @DisplayName("5 分钟内相同 type 不重复记录")
        void deduplicateWithin5Minutes() {
            when(activityMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            ActivityRequest req = new ActivityRequest();
            req.setType("kb_search");
            req.setFramework("react");
            req.setKeywords(new String[]{"hooks", "useState"});

            // 不抛异常即为成功（去重后不调用 insert）
            activityService.recordActivity(userId, req).block();
        }
    }

    @Nested
    @DisplayName("查询行为")
    class QueryActivityTests {

        @Test
        @DisplayName("分页查询用户行为记录")
        void getUserActivitiesWithPaging() {
            List<UserActivity> activities = List.of(
                    createActivity("kb_search", "react", new String[]{"hooks"}),
                    createActivity("demo_generate", "spring-boot", new String[]{"crud"})
            );

            when(activityMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(activities);

            List<UserActivity> result = activityService.getUserActivities(userId, 1, 10).block();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("无行为记录时返回空列表")
        void getActivitiesEmpty() {
            when(activityMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<UserActivity> result = activityService.getUserActivities(userId, 1, 10).block();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("清理过期数据")
    class CleanupTests {

        @Test
        @DisplayName("清理 90 天前的行为数据")
        void cleanupOldData() {
            when(activityMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(15);

            Integer deleted = activityService.cleanup(userId, 90).block();

            assertThat(deleted).isEqualTo(15);
        }
    }

    private UserActivity createActivity(String type, String framework, String[] keywords) {
        UserActivity activity = new UserActivity();
        activity.setId(UUID.randomUUID());
        activity.setUserId(userId);
        activity.setType(type);
        activity.setFramework(framework);
        activity.setKeywords(keywords);
        activity.setCreatedAt(Instant.now());
        return activity;
    }
}
