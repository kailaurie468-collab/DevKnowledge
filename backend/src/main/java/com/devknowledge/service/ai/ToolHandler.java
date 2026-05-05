package com.devknowledge.service.ai;

/**
 * 工具调用处理器
 * AI 发出工具调用请求后，由该接口的实现执行实际逻辑并返回结果
 */
@FunctionalInterface
public interface ToolHandler {

    /**
     * 执行工具调用
     *
     * @param arguments JSON 格式的参数
     * @return 工具执行结果（文本）
     */
    String apply(String arguments);
}
