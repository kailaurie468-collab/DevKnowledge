package com.devknowledge.service.ai;

import com.devknowledge.model.UserAiConfig;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 服务商统一适配器接口
 * 屏蔽不同服务商 API 格式差异，提供统一的流式调用入口
 */
public interface AiProviderAdapter {

    /**
     * 流式对话（普通文本输出）
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户输入
     * @param config       用户的 AI 配置
     * @return 文本片段流
     */
    Flux<String> streamCompletion(String systemPrompt, String userMessage, UserAiConfig config);

    /**
     * 支持 Function Calling 的流式对话
     * AI 可以自主决定调用哪些工具，后端执行工具后将结果注入对话继续推理
     *
     * @param systemPrompt 系统提示词
     * @param messages     完整对话历史（包含 system/user/assistant/tool 消息）
     * @param tools        AI 可调用的工具列表
     * @param config       用户的 AI 配置
     * @return AI 输出块流（文本 / 工具调用请求）
     */
    Flux<AiChunk> streamWithTools(String systemPrompt, List<ChatMessage> messages,
                                   List<AiFunction> tools, UserAiConfig config);

    /** 获取该适配器支持的 provider 名称 */
    String getProviderName();

    /**
     * 对话消息
     * @param role            角色：system / user / assistant / tool
     * @param content         消息内容
     * @param name            工具名（role=tool 时必填，其他情况为 null）
     * @param reasoningContent 思考过程（role=assistant 时，保留模型的 reasoning_content）
     */
    record ChatMessage(String role, String content, String name, String reasoningContent) {
        ChatMessage(String role, String content) {
            this(role, content, null, null);
        }

        ChatMessage(String role, String content, String name) {
            this(role, content, name, null);
        }
    }
}
