package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.mapper.SkillSuggestionMapper;
import com.devknowledge.mapper.UserActivityMapper;
import com.devknowledge.model.Skill;
import com.devknowledge.model.SkillSuggestion;
import com.devknowledge.model.SkillStep;
import com.devknowledge.model.UserActivity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill 推荐服务
 * Phase 1: 规则引擎推荐 + 推荐 CRUD
 */
@Service
@RequiredArgsConstructor
public class SkillSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SkillSuggestionService.class);

    private final SkillSuggestionMapper suggestionMapper;
    private final UserActivityMapper activityMapper;
    private final SkillService skillService;

    // ==================== 预设模板库 ====================

    private static final List<Map<String, Object>> TEMPLATES = List.of(
        Map.of("name", "CRUD API 开发",
               "framework", "spring-boot",
               "keywords", List.of("crud", "api", "rest", "controller"),
               "category", "backend",
               "description", "基于 Spring Boot 的 RESTful CRUD API 开发流程"),
        Map.of("name", "React 组件开发",
               "framework", "react",
               "keywords", List.of("component", "react", "tsx", "hooks"),
               "category", "frontend",
               "description", "React 组件开发标准流程，包含类型定义、测试和文档"),
        Map.of("name", "数据库迁移",
               "framework", "postgresql",
               "keywords", List.of("migration", "database", "sql", "schema"),
               "category", "database",
               "description", "数据库 Schema 变更的标准迁移流程"),
        Map.of("name", "API 测试编写",
               "framework", "",
               "keywords", List.of("test", "testing", "api", "assert"),
               "category", "testing",
               "description", "API 接口测试的标准编写流程")
    );

    // ==================== 推荐 CRUD ====================

    /**
     * 获取用户的推荐列表（pending 状态）
     */
    public Mono<List<SkillSuggestion>> getSuggestions(UUID userId) {
        return Mono.fromCallable(() ->
            suggestionMapper.selectList(
                new LambdaQueryWrapper<SkillSuggestion>()
                    .eq(SkillSuggestion::getUserId, userId)
                    .eq(SkillSuggestion::getStatus, "pending")
                    .orderByDesc(SkillSuggestion::getCreatedAt))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 编辑推荐（采纳前修改内容）
     */
    public Mono<SkillSuggestion> updateSuggestion(UUID id, UUID userId, SkillSuggestion partial) {
        return Mono.fromCallable(() -> {
            SkillSuggestion existing = suggestionMapper.selectById(id);
            if (existing == null) throw new RuntimeException("推荐不存在");
            if (!existing.getUserId().equals(userId)) throw new RuntimeException("无权修改此推荐");

            if (partial.getName() != null) existing.setName(partial.getName());
            if (partial.getDescription() != null) existing.setDescription(partial.getDescription());
            if (partial.getTriggerDescription() != null) existing.setTriggerDescription(partial.getTriggerDescription());
            if (partial.getCategory() != null) existing.setCategory(partial.getCategory());
            if (partial.getSuggestedSteps() != null) existing.setSuggestedSteps(partial.getSuggestedSteps());
            existing.setUpdatedAt(Instant.now());
            suggestionMapper.updateById(existing);
            return existing;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 采纳推荐 → 转为正式 Skill + 删除推荐
     */
    public Mono<Skill> acceptSuggestion(UUID id, UUID userId) {
        return Mono.fromCallable(() -> {
            SkillSuggestion suggestion = suggestionMapper.selectById(id);
            if (suggestion == null) throw new RuntimeException("推荐不存在");
            if (!suggestion.getUserId().equals(userId)) throw new RuntimeException("无权操作此推荐");

            return suggestion;
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(suggestion -> {
            // 构建 Skill 对象
            Skill skill = new Skill();
            skill.setName(suggestion.getName());
            skill.setDescription(suggestion.getDescription());
            skill.setTriggerDescription(suggestion.getTriggerDescription());
            skill.setCategory(suggestion.getCategory());

            // 构建步骤列表
            List<SkillStep> steps = new ArrayList<>();
            if (suggestion.getSuggestedSteps() != null) {
                for (int i = 0; i < suggestion.getSuggestedSteps().size(); i++) {
                    Object stepObj = suggestion.getSuggestedSteps().get(i);
                    if (stepObj instanceof Map<?, ?> stepMap) {
                        SkillStep step = new SkillStep();
                        step.setTitle((String) stepMap.get("title"));
                        step.setDescription((String) stepMap.get("description"));
                        Object stepTypeVal = stepMap.get("stepType");
                        step.setStepType(stepTypeVal != null ? (String) stepTypeVal : "action");
                        step.setCodeTemplate((String) stepMap.get("codeTemplate"));
                        step.setExpectedOutput((String) stepMap.get("expectedOutput"));
                        step.setNotes((String) stepMap.get("notes"));
                        steps.add(step);
                    }
                }
            }

            return skillService.createSkill(userId, skill, steps)
                .flatMap(createdSkill -> {
                    // 标记推荐为已采纳
                    suggestion.setStatus("accepted");
                    suggestion.setUpdatedAt(Instant.now());
                    suggestionMapper.updateById(suggestion);
                    log.info("推荐已采纳: suggestionId={}, skillId={}", id, createdSkill.getId());
                    return Mono.just(createdSkill);
                });
        });
    }

    /**
     * 忽略推荐
     */
    public Mono<Void> dismissSuggestion(UUID id, UUID userId) {
        return Mono.fromRunnable(() -> {
            SkillSuggestion suggestion = suggestionMapper.selectById(id);
            if (suggestion == null) throw new RuntimeException("推荐不存在");
            if (!suggestion.getUserId().equals(userId)) throw new RuntimeException("无权操作此推荐");

            suggestion.setStatus("dismissed");
            suggestion.setUpdatedAt(Instant.now());
            suggestionMapper.updateById(suggestion);
            log.info("推荐已忽略: suggestionId={}", id);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ==================== 规则引擎 ====================

    /**
     * 基于规则引擎生成推荐
     * 1. 分析用户近 30 天行为数据
     * 2. 频率分析 + 模式识别 + 模板匹配
     * 3. 去重（排除已 dismissed 的推荐）
     * 4. 保存新推荐
     */
    public Mono<List<SkillSuggestion>> generateSuggestions(UUID userId) {
        return Mono.fromCallable(() -> {
            Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

            // 1. 获取用户近 30 天行为数据
            List<UserActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<UserActivity>()
                    .eq(UserActivity::getUserId, userId)
                    .ge(UserActivity::getCreatedAt, thirtyDaysAgo));

            if (activities.isEmpty()) {
                log.info("用户无行为数据，无法生成推荐: userId={}", userId);
                return new ArrayList<SkillSuggestion>();
            }

            // 2. 频率分析
            Map<String, Integer> frameworkFreq = new HashMap<>();
            Map<String, Integer> keywordFreq = new HashMap<>();
            for (UserActivity a : activities) {
                if (a.getFramework() != null && !a.getFramework().isBlank()) {
                    frameworkFreq.merge(a.getFramework().toLowerCase(), 1, Integer::sum);
                }
                if (a.getKeywords() != null) {
                    for (String kw : a.getKeywords()) {
                        keywordFreq.merge(kw.toLowerCase(), 1, Integer::sum);
                    }
                }
            }

            // 3. 模式识别：同一操作类型重复 3+ 次
            Map<String, Long> typeCount = activities.stream()
                .collect(Collectors.groupingBy(UserActivity::getType, Collectors.counting()));

            // 4. 获取已 dismissed 的推荐名称，用于去重
            Set<String> dismissedNames = suggestionMapper.selectList(
                new LambdaQueryWrapper<SkillSuggestion>()
                    .eq(SkillSuggestion::getUserId, userId)
                    .eq(SkillSuggestion::getStatus, "dismissed"))
                .stream()
                .map(SkillSuggestion::getName)
                .collect(Collectors.toSet());

            // 5. 模板匹配
            List<SkillSuggestion> newSuggestions = new ArrayList<>();
            for (Map<String, Object> template : TEMPLATES) {
                String templateName = (String) template.get("name");
                // 跳过已 dismissed 的模板
                if (dismissedNames.contains(templateName)) continue;

                String tplFramework = ((String) template.get("framework")).toLowerCase();
                List<String> tplKeywords = (List<String>) template.get("keywords");

                // 检查框架匹配
                boolean frameworkMatch = !tplFramework.isBlank()
                        && frameworkFreq.getOrDefault(tplFramework, 0) >= 2;

                // 检查关键词匹配（至少命中 2 个）
                long keywordHits = tplKeywords.stream()
                        .filter(kw -> keywordFreq.getOrDefault(kw, 0) >= 1)
                        .count();
                boolean keywordMatch = keywordHits >= 2;

                if (frameworkMatch || keywordMatch) {
                    // 构建推荐
                    SkillSuggestion suggestion = new SkillSuggestion();
                    suggestion.setId(UUID.randomUUID());
                    suggestion.setUserId(userId);
                    suggestion.setName(templateName);
                    suggestion.setDescription((String) template.get("description"));
                    suggestion.setCategory((String) template.get("category"));
                    suggestion.setSuggestedSteps(new ArrayList<>());
                    suggestion.setSourceSummary(String.format(
                        "基于你近 30 天的使用模式分析：框架 '%s' 使用 %d 次，关键词命中 %d 个",
                        tplFramework,
                        frameworkFreq.getOrDefault(tplFramework, 0),
                        keywordHits));
                    suggestion.setStatus("pending");
                    suggestion.setCreatedAt(Instant.now());
                    suggestion.setUpdatedAt(Instant.now());
                    newSuggestions.add(suggestion);
                }
            }

            // 6. 保存新推荐
            for (SkillSuggestion s : newSuggestions) {
                suggestionMapper.insert(s);
            }

            log.info("规则引擎生成推荐: userId={}, count={}", userId, newSuggestions.size());
            return newSuggestions;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
