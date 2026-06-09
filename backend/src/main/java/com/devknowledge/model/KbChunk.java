package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

/**
 * 将文档切分成的段落chunk
 */
@Data
@TableName("kb_chunks")
public class KbChunk {
    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID kbId;
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID docId;
    private Integer chunkIndex;
    private String content;
    /** 向量："[0.1, 0.2, ...]" 格式，通过 VectorTypeHandler 与 pgvector vector(1536) 互转 */
    @TableField(typeHandler = VectorTypeHandler.class)
    private String embedding;
    private Instant createdAt;

    /** Jieba 分词后的空格分隔词项，用于 BM25 tsvector 检索（非持久化字段，由 mapper 自行处理 ::tsvector 转换） */
    @TableField(exist = false)
    private String tsv;
}
