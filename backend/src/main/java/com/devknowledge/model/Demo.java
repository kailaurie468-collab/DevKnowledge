package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Demo 生成记录实体
 * 对应表：demos
 */
@Data
@TableName("demos")
public class Demo {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    /** 生成该 Demo 的用户 ID（可为空，支持匿名） */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    /** Demo 标题（AI 自动生成） */
    private String title;

    /** 用户输入的 prompt */
    private String prompt;

    /** 关联框架 ID（可选） */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID frameworkId;

    /** 生成的代码内容 */
    private String codeContent;

    /** 代码解释 */
    private String explanation;

    /** 编程语言 */
    private String language;

    /** 标签数组 */
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;

    /** 消耗的 token 数 */
    private Integer tokensUsed;

    /** 使用的模型版本 */
    private String modelVersion;

    private Instant createdAt;
}
