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
                .doOnNext(chunk -> log.info("AI 测试响应: {}", chunk))
                .last()
                .mapNotNull(jsonNode -> {
                    if (jsonNode == null) return null;
                    return jsonNode.has("usage")
                            ? jsonNode.get("usage").get("total_tokens").asText()
                            : null;
                }).flux();
//                .doOnNext(chunk -> log.info("AI 测试响应: {}", chunk))
//                .mapNotNull(chunk -> {
//                    JsonNode delta = extractDelta(chunk);
//                    if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
//                        return delta.get("content").asText();
//                    }
//                    return null;
//                });
    }

    @Override
    public Flux<AiChunk> streamWithTools(String systemPrompt, List<ChatMessage> messages,
                                          List<AiFunction> tools, UserAiConfig config) {
        WebClient client = buildClient(config);

        List<Map<String, String>> msgList = new ArrayList<>();
        msgList.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatMessage msg : messages) {
            Map<String, String> msgMap = new LinkedHashMap<>();
            msgMap.put("role", msg.role());
            msgMap.put("content", msg.content());
            if (msg.name() != null) {
                msgMap.put("name", msg.name());
            }
            // 保留 reasoning_content，模型建议在多轮对话中传递以获得最佳表现
            if (msg.reasoningContent() != null && !msg.reasoningContent().isEmpty()) {
                msgMap.put("reasoning_content", msg.reasoningContent());
            }
            msgList.add(msgMap);
        }

        // 将自定义的tools 按照openai格式 封装进请求的body
        /**
         * {
         *   "model": "gpt-3.5-turbo-1106",
         *   "messages": [
         *     {"role": "user", "content": "帮我查一下今天北京天气"}
         *   ],
         *   "tools": [
         *     // 工具定义列表
         *     {
         *       "type": "function",
         *       "function": {
         *         "name": "get_weather",
         *         "description": "获取指定城市的实时天气",
         *         "parameters": {
         *           "type": "object",
         *           "properties": {
         *             "city": {
         *               "type": "string",
         *               "description": "城市名称，如北京、上海"
         *             }
         *           },
         *           "required": ["city"]
         *         }
         *       }
         *     }
         *   ],
         *   "tool_choice": "auto"
         * }
         */
        List<Map<String, Object>> toolList = new ArrayList<>();
        if (tools != null) {
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
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", msgList);
        body.put("tools", toolList);
        body.put("max_tokens", config.getMaxTokens());
        body.put("stream", true);

        log.info("调用 AI (tools): provider={}, model={}, tools={}", config.getProvider(), config.getModel(), tools.size());

        // 流式处理：文本 chunk 实时发送（打字机效果），工具调用收集完成后统一发送
        // 使用共享状态收集工具调用和 reasoning
        Map<String, Object> sharedState = new java.util.concurrent.ConcurrentHashMap<>();
        sharedState.put("reasoning", new StringBuilder());
        sharedState.put("toolAcc", new LinkedHashMap<Integer, String[]>());

        return postStream(client, body)
                .doOnNext(chunk -> log.debug("收到 chunk: {}", chunk))
                .doOnError(e -> log.error("AI 流式调用错误: {}", e.getMessage()))
                .doOnComplete(() -> log.info("AI 流式调用完成"))
                .concatMap(chunk -> {
                    JsonNode delta = extractDelta(chunk);
                    if (delta == null) return Flux.empty();

                    // 累积 reasoning_content
                    if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                        String r = delta.get("reasoning_content").asText();
                        if (!r.isEmpty()) {
                            ((StringBuilder) sharedState.get("reasoning")).append(r);
                        }
                    }

                    // 收集工具调用（不实时发送，最后统一发送）
                    JsonNode toolCalls = delta.get("tool_calls");
                    if (toolCalls != null && toolCalls.isArray()) {
                        @SuppressWarnings("unchecked")
                        LinkedHashMap<Integer, String[]> toolAcc = (LinkedHashMap<Integer, String[]>) sharedState.get("toolAcc");
                        for (JsonNode tc : toolCalls) {
                            int index = tc.has("index") ? tc.get("index").asInt() : 0;
                            JsonNode fnNode = tc.get("function");
                            if (fnNode == null) continue;

                            String[] slot = toolAcc.computeIfAbsent(index, k -> new String[]{"", ""});
                            if (fnNode.has("name") && !fnNode.get("name").isNull()
                                    && !fnNode.get("name").asText().isEmpty()) {
                                slot[0] = fnNode.get("name").asText();
                            }
                            if (fnNode.has("arguments") && !fnNode.get("arguments").asText().isEmpty()) {
                                slot[1] += fnNode.get("arguments").asText();
                            }
                        }
                        return Flux.empty();
                    }

                    // 实时发送文本 chunk（打字机效果）
                    if (delta.has("content") && !delta.get("content").isNull()) {
                        String c = delta.get("content").asText();
                        if (!c.isEmpty()) {
                            return Flux.just(AiChunk.text(c));
                        }
                    }

                    return Flux.empty();
                })
                /**
                 * 时间 →
                 *
                 * 服务端推送:  chunk1  chunk2  chunk3  chunk4  [DONE] (关闭连接)
                 *               ↓       ↓       ↓       ↓        ↓
                 * postStream:   解析    解析    解析    解析    filter过滤掉 → onComplete信号
                 *               ↓       ↓       ↓       ↓        ↓
                 * concatMap:  处理1   处理2   处理3   处理4    收到onComplete，自身也完成
                 *                                                     ↓
                 * concatWith:                                    前驱完成！触发 Flux.defer()
                 *                                                读取 sharedState，发出工具调用
                 */
                // 流结束后，统一发送工具调用
                .concatWith(Flux.defer(() -> {
                    String reasoning = ((StringBuilder) sharedState.get("reasoning")).toString();
                    @SuppressWarnings("unchecked")
                    LinkedHashMap<Integer, String[]> toolAcc = (LinkedHashMap<Integer, String[]>) sharedState.get("toolAcc");

                    log.info("AI 输出 - reasoning长度: {}, toolCall数量: {}", reasoning.length(), toolAcc.size());

                    List<AiChunk> result = new ArrayList<>();

                    // 发送所有工具调用
                    for (Map.Entry<Integer, String[]> entry : toolAcc.entrySet()) {
                        String fnName = entry.getValue()[0];
                        String fnArgs = entry.getValue()[1];
                        if (fnName.isEmpty()) {
                            log.warn("工具调用 index={} 缺少 function name，跳过", entry.getKey());
                            continue;
                        }
                        if (fnArgs.isEmpty()) fnArgs = "{}";
                        log.info("AI 请求调用工具 [{}]: {}({})", entry.getKey(), fnName, fnArgs);
                        result.add(AiChunk.toolCall(fnName, fnArgs, reasoning));
                    }

                    return Flux.fromIterable(result);
                }));
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
//                .doOnNext(line -> log.debug("原始响应: {}", line))
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
