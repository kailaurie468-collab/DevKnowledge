package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 框架实体
 * 对应表：frameworks
 */
@Data
@TableName("frameworks")
public class Framework {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    /** 框架名称，如 React */
    private String name;

    /** URL 友好标识，如 react */
    private String slug;

    /** 官方文档地址 */
    private String baseUrl;

    /** 图标地址 */
    private String iconUrl;

    /** 框架简介 */
    private String description;

    /** 分类：frontend / backend / mobile */
    private String category;

    private Instant createdAt;
}
