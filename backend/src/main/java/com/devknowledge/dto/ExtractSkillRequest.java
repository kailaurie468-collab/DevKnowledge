package com.devknowledge.dto;

import lombok.Data;

/**
 * Skill 提取请求体
 * 用户描述一段工作流程，AI 从中提取结构化 Skill
 */
@Data
public class ExtractSkillRequest {

    /** 用户描述的工作流程 */
    private String description;

    /** 关联框架 ID（可选，字符串形式的 UUID） */
    private String frameworkId;

    /** 分类（可选） */
    private String category;
}
