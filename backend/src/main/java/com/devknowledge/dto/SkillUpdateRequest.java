package com.devknowledge.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Skill 更新请求体
 * PUT /api/skills/{id}
 */
@Data
public class SkillUpdateRequest {

    /** Skill 名称 */
    private String name;

    /** Skill 描述 */
    private String description;

    /** 分类标签 */
    private String category;

    /** 触发条件描述 */
    private String triggerDescription;

    /** 步骤列表（完整替换） */
    private List<StepItem> steps;

    /**
     * 步骤项（内嵌 DTO）
     */
    @Data
    public static class StepItem {
        /** 步骤 ID（有值=更新，无值=新增） */
        private UUID id;
        /** 步骤排序号 */
        private Integer stepOrder;
        /** 步骤标题 */
        private String title;
        /** 步骤详细描述 */
        private String description;
        /** 步骤类型：action / decision / validation / reference */
        private String stepType;
        /** 代码模板 */
        private String codeTemplate;
        /** 预期输出 */
        private String expectedOutput;
        /** 补充说明 */
        private String notes;
    }
}
