package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("wiki_index")
public class WikiIndex {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    private String pagePath;
    private String title;
    private String category;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;

    private String summary;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] docIds;

    private Instant updatedAt;
}
