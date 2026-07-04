package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.SkillUpdateRequest;
import com.devknowledge.mapper.SkillMapper;
import com.devknowledge.model.Skill;
import com.devknowledge.model.SkillStep;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Skill 核心服务
 * Skill CRUD + 分页搜索 + Markdown 导出
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final SkillMapper skillMapper;
    private final SkillStepService skillStepService;

    /**
     * 创建 Skill（含步骤联动插入）
     */
    public Mono<Skill> createSkill(UUID userId, Skill skill, List<SkillStep> steps) {
        return Mono.fromCallable(() -> {
            // 设置 Skill 基本字段
            skill.setId(UUID.randomUUID());
            skill.setUserId(userId);
            if (skill.getVersion() == null) skill.setVersion(1);
            if (skill.getIsPublic() == null) skill.setIsPublic(false);
            if (skill.getIsDeleted() == null) skill.setIsDeleted(false);
            skill.setCreatedAt(Instant.now());
            skill.setUpdatedAt(Instant.now());
            skillMapper.insert(skill);

            // 联动插入步骤（replaceSteps 对新 Skill 等价于 insert）
            skillStepService.replaceSteps(skill.getId(), steps);
            skill.setSteps(skillStepService.getStepsBySkillId(skill.getId()));

            log.info("Skill 已创建: id={}, name={}", skill.getId(), skill.getName());
            return skill;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 分页查询用户 Skill 列表（支持分类和关键词过滤）
     */
    public Mono<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Skill>> getUserSkills(
            UUID userId, String category, String keyword, int page, int size) {
        return Mono.fromCallable(() -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Skill> pageParam =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);

            LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<Skill>()
                    .eq(Skill::getUserId, userId)
                    .eq(Skill::getIsDeleted, false)
                    .orderByDesc(Skill::getUpdatedAt);

            // 分类过滤
            if (category != null && !category.isBlank()) {
                wrapper.eq(Skill::getCategory, category);
            }

            // 关键词搜索（name + description 模糊匹配）
            if (keyword != null && !keyword.isBlank()) {
                wrapper.and(w -> w
                        .like(Skill::getName, keyword)
                        .or()
                        .like(Skill::getDescription, keyword));
            }

            return skillMapper.selectPage(pageParam, wrapper);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取 Skill 详情（含所有步骤）
     */
    public Mono<Skill> getSkillById(UUID id, UUID userId) {
        return Mono.fromCallable(() -> {
            Skill skill = skillMapper.selectById(id);
            if (skill == null || skill.getIsDeleted()) return null;
            if (!skill.getUserId().equals(userId)) return null;  // 无权访问

            // 加载步骤列表
            List<SkillStep> steps = skillStepService.getStepsBySkillId(id);
            skill.setSteps(steps != null ? steps : new ArrayList<>());
            return skill;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 更新 Skill（含步骤替换）
     * 每次更新 version + 1
     */
    public Mono<Skill> updateSkill(UUID id, UUID userId, SkillUpdateRequest req) {
        return Mono.fromCallable(() -> {
            Skill skill = skillMapper.selectById(id);
            if (skill == null || skill.getIsDeleted()) {
                throw new RuntimeException("Skill 不存在");
            }
            if (!skill.getUserId().equals(userId)) {
                throw new RuntimeException("无权修改此 Skill");
            }

            // 更新基本字段
            if (req.getName() != null) skill.setName(req.getName());
            if (req.getDescription() != null) skill.setDescription(req.getDescription());
            if (req.getCategory() != null) skill.setCategory(req.getCategory());
            if (req.getTriggerDescription() != null) skill.setTriggerDescription(req.getTriggerDescription());
            skill.setVersion(skill.getVersion() + 1);
            skill.setUpdatedAt(Instant.now());
            skillMapper.updateById(skill);

            // 替换步骤列表
            if (req.getSteps() != null) {
                List<SkillStep> newSteps = new ArrayList<>();
                for (SkillUpdateRequest.StepItem item : req.getSteps()) {
                    SkillStep step = new SkillStep();
                    step.setTitle(item.getTitle());
                    step.setDescription(item.getDescription());
                    step.setStepType(item.getStepType() != null ? item.getStepType() : "action");
                    step.setCodeTemplate(item.getCodeTemplate());
                    step.setExpectedOutput(item.getExpectedOutput());
                    step.setNotes(item.getNotes());
                    newSteps.add(step);
                }
                skillStepService.replaceSteps(id, newSteps);
                skill.setSteps(newSteps);
            }

            log.info("Skill 已更新: id={}, version={}", skill.getId(), skill.getVersion());
            return skill;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 软删除 Skill
     */
    public Mono<Void> deleteSkill(UUID id, UUID userId) {
        return Mono.fromCallable(() -> {
            Skill skill = skillMapper.selectById(id);
            if (skill == null) throw new RuntimeException("Skill 不存在");
            if (!skill.getUserId().equals(userId)) throw new RuntimeException("无权删除此 Skill");

            skill.setIsDeleted(true);
            skill.setUpdatedAt(Instant.now());
            skillMapper.updateById(skill);

            log.info("Skill 已软删除: id={}", id);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 导出 Skill 为 Markdown 文档
     */
    public Mono<String> exportToMarkdown(UUID skillId, UUID userId) {
        return Mono.fromCallable(() -> {
            Skill skill = skillMapper.selectById(skillId);
            if (skill == null || skill.getIsDeleted() || !skill.getUserId().equals(userId)) {
                throw new RuntimeException("无权访问此 Skill");
            }

            List<SkillStep> steps = skillStepService.getStepsBySkillId(skillId);

            StringBuilder md = new StringBuilder();
            md.append("# ").append(skill.getName()).append("\n\n");
            if (skill.getDescription() != null) {
                md.append("**描述**: ").append(skill.getDescription()).append("\n\n");
            }
            if (skill.getTriggerDescription() != null) {
                md.append("**触发条件**: ").append(skill.getTriggerDescription()).append("\n\n");
            }
            if (skill.getCategory() != null) {
                md.append("**分类**: ").append(skill.getCategory()).append("\n\n");
            }

            md.append("## 步骤\n\n");
            if (steps != null) {
                for (SkillStep step : steps) {
                    md.append("### ").append(step.getStepOrder()).append(". ");
                    md.append("[").append(step.getStepType()).append("] ");
                    md.append(step.getTitle()).append("\n\n");
                    if (step.getDescription() != null) {
                        md.append(step.getDescription()).append("\n\n");
                    }
                    if (step.getCodeTemplate() != null) {
                        md.append("```\n").append(step.getCodeTemplate()).append("\n```\n\n");
                    }
                    if (step.getExpectedOutput() != null) {
                        md.append("**预期输出**: ").append(step.getExpectedOutput()).append("\n\n");
                    }
                    if (step.getNotes() != null) {
                        md.append("> ").append(step.getNotes()).append("\n\n");
                    }
                }
            }

            // 缓存导出内容
            String content = md.toString();
            skill.setExportedContent(content);
            skillMapper.updateById(skill);

            log.info("Skill 已导出: id={}, length={}", skillId, content.length());
            return content;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
