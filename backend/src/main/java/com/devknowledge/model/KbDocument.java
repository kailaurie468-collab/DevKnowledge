package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("kb_documents")
public class KbDocument {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID kbId;

    private String filename;
    private String fileType;
    private Long fileSize;
    private String content;
    private String status;
    private String errorMessage;
    /** 非致命警告信息（如未配置 Embedding 时的提示） */
    private String warningMessage;
    /** 文档被切分成的文本片段数量（RAG 向量化时按段落切分，每个片段独立 Embedding） */
    private Integer chunkCount;

    private Instant createdAt;
}
