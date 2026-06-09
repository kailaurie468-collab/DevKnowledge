package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("wiki_relations")
public class WikiRelation {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID sourceId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID targetId;

    private String relation;
    private String description;
    private Double strength;
    private Instant createdAt;
}
