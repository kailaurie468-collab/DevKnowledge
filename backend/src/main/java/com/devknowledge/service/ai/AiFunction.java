package com.devknowledge.service.ai;

/**
 * AI 可调用的工具定义
 * 对应 OpenAI Function Calling 的 function 描述
 *
 * @param name            工具名称，如 "search_kb"
 * @param description     工具描述，AI 据此决定何时调用
 * @param parametersJson  参数的 JSON Schema（字符串形式）
 */
public record AiFunction(
        String name,
        String description,
        String parametersJson
) {}
