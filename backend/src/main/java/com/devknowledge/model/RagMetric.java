package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * RAG 检索指标实体
 * 记录每次 Demo 生成时的 RAG 检索质量数据
 */
@Data
@TableName("rag_metrics")
public class RagMetric {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID demoId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID kbId;

    /** 是否使用了 RAG */
    private Boolean ragUsed;

    /** 检索配置的 top-K */
    private Integer topK;

    /** 实际命中 chunk 数 */
    private Integer chunkCount;

    /** top-K 平均相似度 */
    private Double avgSimilarity;

    /** 最高相似度 */
    private Double maxSimilarity;

    /** 最低相似度 */
    private Double minSimilarity;

    /** 检索耗时（毫秒） */
    private Integer retrievalMs;

    /** search_kb 工具被调用次数 */
    private Integer toolCallCount;

    private Instant createdAt;
}
