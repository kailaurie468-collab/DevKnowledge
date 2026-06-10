package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@TableName("user_embedding_configs")
public class UserEmbeddingConfig {
    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;
    private String name;
    private String apiKey;
    private String baseUrl;
    private String modelName;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
