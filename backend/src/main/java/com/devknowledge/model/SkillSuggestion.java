package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Skill 推荐实体
 * 对应表：skill_suggestions，AI/规则引擎生成的推荐
 */
@Data
@TableName(value = "skill_suggestions", autoResultMap = true)
public class SkillSuggestion {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    /** 所属用户 ID */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    /** 推荐名称 */
    private String name;

    /** 推荐描述 */
    private String description;

    /** 触发条件描述 */
    private String triggerDescription;

    /** 分类 */
    private String category;

    /** 推荐的步骤列表（JSONB 存储） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> suggestedSteps;

    /** 推荐来源摘要 */
    private String sourceSummary;

    /** 状态：pending / accepted / dismissed */
    private String status;

    private Instant createdAt;

    private Instant updatedAt;
}
