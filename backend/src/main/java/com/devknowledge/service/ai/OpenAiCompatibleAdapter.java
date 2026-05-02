package com.devknowledge.service.ai;

import com.devknowledge.model.UserAiConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * OpenAI 兼容格式适配器
 * 支持所有兼容 OpenAI API 格式的服务商：OpenAI、DeepSeek、小米、自定义
 */
@Component
public class OpenAiCompatibleAdapter implements AiProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAdapter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getProviderName() {
        return "openai-compatible";
    }

    @Override
    public Flux<String> streamCompletion(String systemPrompt, String userMessage, UserAiConfig config) {
        WebClient client = buildClient(config);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));
        body.put("max_tokens", config.getMaxTokens());
        body.put("stream", true);

        return postStream(client, body)
                .mapNotNull(chunk -> {
                    JsonNode delta = extractDelta(chunk);
                    if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                        return delta.get("content").asText();
                    }
                    return null;
                });
    }

    @Override
    public Flux<AiChunk> streamWithTools(String systemPrompt, List<ChatMessage> messages,
                                          List<AiFunction> tools, UserAiConfig config) {
        WebClient client = buildClient(config);

        List<Map<String, String>> msgList = new ArrayList<>();
        msgList.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatMessage msg : messages) {
            msgList.add(Map.of("role", msg.role(), "content", msg.content()));
        }

        List<Map<String, Object>> toolList = new ArrayList<>();
        for (AiFunction fn : tools) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", fn.name());
            function.put("description", fn.description());
            try {
                function.put("parameters", objectMapper.readTree(fn.parametersJson()));
            } catch (JsonProcessingException e) {
                function.put("parameters", Map.of("type", "object", "properties", Map.of()));
            }
            tool.put("function", function);
            toolList.add(tool);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", msgList);
        body.put("tools", toolList);
        body.put("max_tokens", config.getMaxTokens());
        body.put("stream", true);

        log.info("调用 AI (tools): provider={}, model={}, tools={}", config.getProvider(), config.getModel(), tools.size());

        // 先收集所有 chunk，再统一解析
        // 原因：小米模型的 reasoning_content 和 content 是分开的，需要先看完整输出再决定
        return postStream(client, body)
                .doOnNext(chunk -> log.debug("收到 chunk: {}", chunk))
                .doOnError(e -> log.error("AI 流式调用错误: {}", e.getMessage()))
                .doOnComplete(() -> log.info("AI 流式调用完成"))
                .collect(() -> new String[]{"", "", ""}, (acc, chunk) -> {
                    JsonNode delta = extractDelta(chunk);
                    if (delta == null) return;

                    // 工具调用
                    JsonNode toolCalls = delta.get("tool_calls");
                    if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty()) {
                        JsonNode tc = toolCalls.get(0);
                        JsonNode fnNode = tc.get("function");
                        if (fnNode != null && fnNode.has("name")) {
                            String fnName = fnNode.get("name").asText();
                            String fnArgs = fnNode.has("arguments") ? fnNode.get("arguments").asText() : "{}";
                            log.info("AI 请求调用工具: {}({})", fnName, fnArgs);
                            acc[2] = fnName + ":" + fnArgs;
                        }
                    }

                    // 累积 reasoning_content（思考过程）
                    if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                        String r = delta.get("reasoning_content").asText();
                        if (!r.isEmpty()) acc[0] += r;
                    }

                    // 累积 content（最终回复）
                    if (delta.has("content") && !delta.get("content").isNull()) {
                        String c = delta.get("content").asText();
                        if (!c.isEmpty()) acc[1] += c;
                    }
                })
                .flatMapMany(acc -> {
                    String reasoning = acc[0];
                    String content = acc[1];
                    String toolCall = acc[2];
                    log.info("AI 输出 - reasoning长度: {}, content长度: {}, toolCall: {}",
                            reasoning.length(), content.length(), toolCall.isEmpty() ? "无" : toolCall);

                    List<AiChunk> result = new ArrayList<>();

                    // 有工具调用
                    if (!toolCall.isEmpty()) {
                        int idx = toolCall.indexOf(":");
                        if (idx > 0) {
                            result.add(AiChunk.toolCall(toolCall.substring(0, idx), toolCall.substring(idx + 1)));
                        }
                    }

                    // 只输出 content（模型的实际回复），不输出 reasoning
                    // reasoning 是模型内部思考过程，不应展示给用户
                    if (!content.isEmpty()) {
                        result.add(AiChunk.text(content));
                    } else if (result.isEmpty()) {
                        // content 为空且没有工具调用，说明模型没有产生有效输出
                        log.warn("AI 未产生 content 输出，reasoning: {}", reasoning.substring(0, Math.min(reasoning.length(), 100)));
                        result.add(AiChunk.text("[AI 未产生输出，请重试]"));
                    }

                    return Flux.fromIterable(result);
                });
    }

    // ==================== 内部方法 ====================

    private WebClient buildClient(UserAiConfig config) {
        return WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private Flux<JsonNode> postStream(WebClient client, Map<String, Object> body) {
        return client.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(line -> log.debug("原始响应: {}", line))
                .filter(line -> line != null && !line.isBlank() && !line.equals("[DONE]"))
                .mapNotNull(line -> {
                    try {
                        return objectMapper.readTree(line);
                    } catch (Exception e) {
                        log.debug("解析 chunk 失败: {}", line);
                        return null;
                    }
                });
    }

    private JsonNode extractDelta(JsonNode node) {
        JsonNode choices = node.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).get("delta");
        }
        return null;
    }
}
