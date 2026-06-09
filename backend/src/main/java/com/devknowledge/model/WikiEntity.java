package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("wiki_entities")
public class WikiEntity {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    private String name;
    private String type;
    private String description;
    private String pagePath;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID docId;

    private Instant createdAt;
    private Instant updatedAt;
}
