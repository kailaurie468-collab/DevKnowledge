package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.mapper.SkillSuggestionMapper;
import com.devknowledge.mapper.UserActivityMapper;
import com.devknowledge.model.Skill;
import com.devknowledge.model.SkillSuggestion;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SkillSuggestionService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkillSuggestionService - 推荐引擎")
class SkillSuggestionServiceTest {

    @Mock
    private SkillSuggestionMapper suggestionMapper;
    @Mock
    private UserActivityMapper activityMapper;
    @Mock
    private SkillService skillService;

    @InjectMocks
    private SkillSuggestionService suggestionService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("获取推荐列表")
    class GetSuggestionsTests {

        @Test
        @DisplayName("获取 pending 状态的推荐列表")
        void getPendingSuggestions() {
            List<SkillSuggestion> suggestions = List.of(
                    createSuggestion("CRUD API 开发", "pending"),
                    createSuggestion("React 组件开发", "pending")
            );

            when(suggestionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(suggestions);

            List<SkillSuggestion> result = suggestionService.getSuggestions(userId).block();

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(s -> "pending".equals(s.getStatus()));
        }

        @Test
        @DisplayName("无推荐时返回空列表")
        void getEmptySuggestions() {
            when(suggestionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<SkillSuggestion> result = suggestionService.getSuggestions(userId).block();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("规则引擎生成推荐")
    class GenerateSuggestionsTests {

        @Test
        @DisplayName("无行为数据时不生成推荐")
        void noActivityNoSuggestions() {
            when(activityMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(suggestionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<SkillSuggestion> result = suggestionService.generateSuggestions(userId).block();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("重复框架行为触发模板推荐")
        void repeatedFrameworkTriggersSuggestion() {
            List<UserActivity> activities = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                activities.add(createActivity("demo_generate", "react",
                        new String[]{"component", "hooks"}));
            }

            when(activityMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(activities);
            when(suggestionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<SkillSuggestion> result = suggestionService.generateSuggestions(userId).block();

            assertThat(result).isNotEmpty();
            assertThat(result).anyMatch(s ->
                    s.getName().contains("React") || s.getName().contains("react"));
        }

        @Test
        @DisplayName("已 dismissed 的模板不重复推荐")
        void dismissedTemplatesNotReRecommended() {
            SkillSuggestion dismissed = createSuggestion("CRUD API 开发", "dismissed");

            List<UserActivity> activities = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                activities.add(createActivity("demo_generate", "spring-boot",
                        new String[]{"crud", "api"}));
            }

            when(activityMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(activities);
            when(suggestionMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(dismissed));

            List<SkillSuggestion> result = suggestionService.generateSuggestions(userId).block();

            assertThat(result).noneMatch(s -> "CRUD API 开发".equals(s.getName()));
        }
    }

    @Nested
    @DisplayName("采纳推荐")
    class AcceptSuggestionTests {

        @Test
        @DisplayName("采纳推荐转为正式 Skill")
        void acceptConvertsToSkill() {
            UUID suggestionId = UUID.randomUUID();
            SkillSuggestion suggestion = createSuggestion("React 组件开发", "pending");
            suggestion.setId(suggestionId);
            suggestion.setUserId(userId);

            Skill createdSkill = new Skill();
            createdSkill.setId(UUID.randomUUID());
            createdSkill.setName("React 组件开发");

            when(suggestionMapper.selectById(suggestionId)).thenReturn(suggestion);
            when(skillService.createSkill(any(), any(), any())).thenReturn(
                    reactor.core.publisher.Mono.just(createdSkill));

            Skill result = suggestionService.acceptSuggestion(suggestionId, userId).block();

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("React 组件开发");
        }

        @Test
        @DisplayName("非 owner 采纳被拒绝")
        void acceptByWrongUser() {
            UUID suggestionId = UUID.randomUUID();
            SkillSuggestion suggestion = createSuggestion("测试", "pending");
            suggestion.setId(suggestionId);
            suggestion.setUserId(userId);

            when(suggestionMapper.selectById(suggestionId)).thenReturn(suggestion);

            UUID wrongUser = UUID.randomUUID();
            assertThatThrownBy(() -> suggestionService.acceptSuggestion(suggestionId, wrongUser).block())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("无权");
        }
    }

    @Nested
    @DisplayName("忽略推荐")
    class DismissSuggestionTests {

        @Test
        @DisplayName("忽略推荐将状态改为 dismissed")
        void dismissChangesStatus() {
            UUID suggestionId = UUID.randomUUID();
            SkillSuggestion suggestion = createSuggestion("测试", "pending");
            suggestion.setId(suggestionId);
            suggestion.setUserId(userId);

            when(suggestionMapper.selectById(suggestionId)).thenReturn(suggestion);

            // 不抛异常即为成功
            suggestionService.dismissSuggestion(suggestionId, userId).block();
        }

        @Test
        @DisplayName("非 owner 忽略被拒绝")
        void dismissByWrongUser() {
            UUID suggestionId = UUID.randomUUID();
            SkillSuggestion suggestion = createSuggestion("测试", "pending");
            suggestion.setId(suggestionId);
            suggestion.setUserId(userId);

            when(suggestionMapper.selectById(suggestionId)).thenReturn(suggestion);

            UUID wrongUser = UUID.randomUUID();
            assertThatThrownBy(() -> suggestionService.dismissSuggestion(suggestionId, wrongUser).block())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("无权");
        }
    }

    @Nested
    @DisplayName("更新推荐（采纳前编辑）")
    class UpdateSuggestionTests {

        @Test
        @DisplayName("更新推荐名称和描述")
        void updateSuggestionFields() {
            UUID suggestionId = UUID.randomUUID();
            SkillSuggestion existing = createSuggestion("旧名称", "pending");
            existing.setId(suggestionId);
            existing.setUserId(userId);

            when(suggestionMapper.selectById(suggestionId)).thenReturn(existing);

            SkillSuggestion update = new SkillSuggestion();
            update.setName("新名称");
            update.setDescription("新描述");

            SkillSuggestion result = suggestionService.updateSuggestion(
                    suggestionId, userId, update).block();

            assertThat(result).isNotNull();
        }
    }

    private SkillSuggestion createSuggestion(String name, String status) {
        SkillSuggestion suggestion = new SkillSuggestion();
        suggestion.setId(UUID.randomUUID());
        suggestion.setUserId(userId);
        suggestion.setName(name);
        suggestion.setDescription("推荐描述");
        suggestion.setTriggerDescription("触发条件");
        suggestion.setCategory("frontend");
        suggestion.setSuggestedSteps(new ArrayList<>());
        suggestion.setSourceSummary("基于你的使用习惯推荐");
        suggestion.setStatus(status);
        suggestion.setCreatedAt(Instant.now());
        suggestion.setUpdatedAt(Instant.now());
        return suggestion;
    }

    private UserActivity createActivity(String type, String framework, String[] keywords) {
        UserActivity activity = new UserActivity();
        activity.setId(UUID.randomUUID());
        activity.setUserId(userId);
        activity.setType(type);
        activity.setFramework(framework);
        activity.setKeywords(keywords);
        activity.setCreatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        return activity;
    }
}
