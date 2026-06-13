package com.devknowledge.service.ai;

/**
 * AI 输出块
 * 表示流式输出中的一个片段，可以是文本、工具调用请求或结束标记
 *
 * webFlux 创建Sse格式：
 * data: {"type":"TEXT","content":"Hello world"}
 * data: {"type":"TOOL_CALL","functionName":"search","arguments":"{...}"}
 * data: {"type":"DONE"}
 */
public class AiChunk {

    /** 块类型 */
    private final AiChunkType type;

    /** 文本内容（type = TEXT 时） */
    private final String content;

    /** 工具调用的函数名（type = TOOL_CALL 时） */
    private final String functionName;

    /** 工具调用的 JSON 参数（type = TOOL_CALL 时） */
    private final String arguments;

    /** 思考过程（type = REASONING 时，或多轮对话中传递 reasoning_content） */
    private final String reasoningContent;

    private AiChunk(AiChunkType type, String content, String functionName, String arguments, String reasoningContent) {
        this.type = type;
        this.content = content;
        this.functionName = functionName;
        this.arguments = arguments;
        this.reasoningContent = reasoningContent;
    }

    /** 创建文本块 */
    public static AiChunk text(String content) {
        return new AiChunk(AiChunkType.TEXT, content, null, null, null);
    }

    /** 创建工具调用块 */
    public static AiChunk toolCall(String functionName, String arguments) {
        return new AiChunk(AiChunkType.TOOL_CALL, null, functionName, arguments, null);
    }

    /** 创建工具调用块（思考模式下带 reasoning_content） */
    public static AiChunk toolCall(String functionName, String arguments, String reasoningContent) {
        return new AiChunk(AiChunkType.TOOL_CALL, null, functionName, arguments, reasoningContent);
    }

    /** 创建完成标记块 */
    public static AiChunk done() {
        return new AiChunk(AiChunkType.DONE, null, null, null, null);
    }

    /** 创建错误块 */
    public static AiChunk error(String message) {
        return new AiChunk(AiChunkType.ERROR, message, null, null, null);
    }

    public AiChunkType getType() { return type; }
    public String getContent() { return content; }
    public String getFunctionName() { return functionName; }
    public String getArguments() { return arguments; }
    public String getReasoningContent() { return reasoningContent; }
}
