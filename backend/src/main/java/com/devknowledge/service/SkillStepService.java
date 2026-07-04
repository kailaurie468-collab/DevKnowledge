package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.mapper.SkillStepMapper;
import com.devknowledge.model.SkillStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Skill 步骤服务
 * 管理 Skill 的有序步骤列表
 * 注意：所有方法均为同步调用，调用方需在 Schedulers.boundedElastic() 线程中执行
 */
@Service
@RequiredArgsConstructor
public class SkillStepService {

    private final SkillStepMapper stepMapper;

    /**
     * 查询某个 Skill 的所有步骤（按 stepOrder 排序）
     * 同步方法，调用方需在 boundedElastic 线程中
     */
    public List<SkillStep> getStepsBySkillId(UUID skillId) {
        return stepMapper.selectList(
            new LambdaQueryWrapper<SkillStep>()
                .eq(SkillStep::getSkillId, skillId)
                .orderByAsc(SkillStep::getStepOrder));
    }

    /**
     * 替换 Skill 的所有步骤（先删后插，事务保护）
     * 被 SkillService.updateSkill 调用时，外层已有 boundedElastic 调度
     */
    @Transactional
    public void replaceSteps(UUID skillId, List<SkillStep> newSteps) {
        // 1. 删除旧步骤
        stepMapper.delete(
            new LambdaQueryWrapper<SkillStep>()
                .eq(SkillStep::getSkillId, skillId));

        // 2. 插入新步骤，维护 stepOrder
        for (int i = 0; i < newSteps.size(); i++) {
            SkillStep step = newSteps.get(i);
            step.setId(UUID.randomUUID());
            step.setSkillId(skillId);
            step.setStepOrder(i + 1);
            step.setCreatedAt(Instant.now());
            step.setUpdatedAt(Instant.now());
            stepMapper.insert(step);
        }
    }

    /**
     * 批量插入步骤（用于创建 Skill 时）
     */
    public void insertSteps(UUID skillId, List<SkillStep> steps) {
        for (int i = 0; i < steps.size(); i++) {
            SkillStep step = steps.get(i);
            if (step.getId() == null) step.setId(UUID.randomUUID());
            step.setSkillId(skillId);
            step.setStepOrder(i + 1);
            if (step.getCreatedAt() == null) step.setCreatedAt(Instant.now());
            if (step.getUpdatedAt() == null) step.setUpdatedAt(Instant.now());
            stepMapper.insert(step);
        }
    }

    /**
     * 删除某个 Skill 的所有步骤
     */
    public void deleteBySkillId(UUID skillId) {
        stepMapper.delete(
            new LambdaQueryWrapper<SkillStep>()
                .eq(SkillStep::getSkillId, skillId));
    }
}
