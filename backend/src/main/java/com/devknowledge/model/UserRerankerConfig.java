package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

/**
 * 用户 Reranker 配置（精排模型 API 凭据）
 */
@Data
@TableName("user_reranker_configs")
public class UserRerankerConfig {
    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;
    private String name;
    private String apiKey;
    private String baseUrl;
    private String model;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
