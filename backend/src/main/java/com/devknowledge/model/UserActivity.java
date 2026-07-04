package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 用户行为记录实体
 * 对应表：user_activities，为推荐引擎提供数据基础
 */
@Data
@TableName("user_activities")
public class UserActivity {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    /** 所属用户 ID */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    /** 行为类型：demo_generate / kb_search / skill_extract 等 */
    private String type;

    /** 使用的框架 */
    private String framework;

    /** 关键词数组（TEXT[]） */
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] keywords;

    /** 编程语言 */
    private String language;

    /** 结果数量 */
    private Integer resultCount;

    /** 额外元数据（JSONB） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    private Instant createdAt;
}
