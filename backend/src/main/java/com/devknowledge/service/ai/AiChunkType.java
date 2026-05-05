package com.devknowledge.service.ai;

/**
 * AI 输出块类型
 * 对应 SSE 前端事件类型
 */
public enum AiChunkType {

    /** AI 的推理思考过程 */
    THOUGHT,

    /** 普通文本输出 */
    TEXT,

    /** AI 请求调用工具 */
    TOOL_CALL,

    /** 工具返回结果（注入到对话上下文中） */
    TOOL_RESULT,

    /** 生成完成 */
    DONE,

    /** 错误 */
    ERROR
}
