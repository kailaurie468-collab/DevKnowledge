package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@TableName("embedding_usage")
public class EmbeddingUsage {
    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID configId;
    private Integer promptTokens;
    private Instant createdAt;
}
