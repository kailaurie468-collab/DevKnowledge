package com.devknowledge.dto;

import lombok.Data;

import java.util.UUID;

/**
 * Demo 生成请求体
 */
@Data
public class GenerateDemoRequest {

    /** 用户的 prompt 描述 */
    private String prompt;

    /** 关联框架 ID（可选） */
    private UUID frameworkId;

    /** 编程语言 */
    private String language;

    /** 最大推理轮数（可选，默认 5，范围 1-8） */
    private Integer maxIterations;

    /** 关联知识库 ID（可选，传入后 ReActAgent 会使用知识库搜索工具） */
    private UUID kbId;
}
