package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Skill 实体
 * 对应表：skills，存储用户提取或手动创建的 Skill
 */
@Data
@TableName("skills")
public class Skill {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    /** 所属用户 ID */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    /** Skill 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 分类（frontend / backend / devops / database / testing / other） */
    private String category;

    /** 关联框架 ID（可选） */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID frameworkId;

    /** 触发条件描述 */
    private String triggerDescription;

    /** 导出的 Markdown 内容 */
    private String exportedContent;

    /** 版本号，每次更新 +1 */
    private Integer version;

    /** 是否公开 */
    private Boolean isPublic;

    /** 软删除标记 */
    private Boolean isDeleted;

    private Instant createdAt;

    private Instant updatedAt;

    /** 步骤列表（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private List<SkillStep> steps;
}
