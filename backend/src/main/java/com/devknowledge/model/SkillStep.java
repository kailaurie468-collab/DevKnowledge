package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Skill 步骤实体
 * 对应表：skill_steps，每个 Skill 包含有序的步骤列表
 */
@Data
@TableName("skill_steps")
public class SkillStep {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    /** 所属 Skill ID */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID skillId;

    /** 步骤排序号（从 1 开始） */
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

    private Instant createdAt;

    private Instant updatedAt;
}
