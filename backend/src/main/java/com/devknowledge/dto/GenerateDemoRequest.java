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
}
