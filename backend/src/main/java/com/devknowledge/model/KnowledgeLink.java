package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识链接实体
 * 对应表：knowledge_links
 */
@Data
@TableName("knowledge_links")
public class KnowledgeLink {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    /** 所属框架 ID */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID frameworkId;

    /** 链接标题 */
    private String title;

    /** 文档 URL */
    private String url;

    /** 锚点，用于深链接跳转 */
    private String anchor;

    /** 链接描述 */
    private String description;

    /** 标签数组（PostgreSQL text[]） */
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;

    /** 热度分 */
    private Integer popularityScore;

    private Instant createdAt;

    private Instant updatedAt;
}
