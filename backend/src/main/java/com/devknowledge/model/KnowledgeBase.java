package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("knowledge_bases")
public class KnowledgeBase {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    private String name;
    private String description;

    private Instant createdAt;
    private Instant updatedAt;
    /** Embedding 模型名（创建时锁定） */
    private String embeddingModel;

    /** 排序顺序（用户自定义拖拽排序） */
    private Integer sortOrder;
}
