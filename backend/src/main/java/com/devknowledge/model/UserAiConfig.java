package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户 AI 服务商配置实体
 * 对应表：user_ai_configs
 */
@Data
@TableName("user_ai_configs")
public class UserAiConfig {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    /** 关联用户 ID */
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    /** AI 服务商类型：openai / anthropic / deepseek / custom */
    private String provider;

    /** AES 加密后的 API Key 密文 */
    private String apiKey;

    /** API 基础地址 */
    private String baseUrl;

    /** 模型名称 */
    private String model;

    /** 单次最大输出 token 数 */
    private Integer maxTokens;

    private Instant createdAt;

    private Instant updatedAt;
}
