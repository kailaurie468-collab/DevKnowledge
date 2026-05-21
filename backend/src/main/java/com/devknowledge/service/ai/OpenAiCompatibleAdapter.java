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
            Map<String, String> msgMap = new LinkedHashMap<>();
            msgMap.put("role", msg.role());
            msgMap.put("content", msg.content());
            if (msg.name() != null) {
                msgMap.put("name", msg.name());
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
        for (AiFunction fn : tools) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", fn.name());
            function.put("description", fn.description()); // 何时调用tool
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
        // 累加器结构：[0]=reasoning, [1]=content, [2..n]=tool call JSONs
        return postStream(client, body)
                .doOnNext(chunk -> log.debug("收到 chunk: {}", chunk))
                .doOnError(e -> log.error("AI 流式调用错误: {}", e.getMessage()))
                .doOnComplete(() -> log.info("AI 流式调用完成"))
                .collect(
                        () -> new Object[]{"", "", new LinkedHashMap<Integer, String[]>()},
                        (acc, chunk) -> {
                            String reasoning = (String) acc[0];
                            String content = (String) acc[1];
                            @SuppressWarnings("unchecked")
                            LinkedHashMap<Integer, String[]> toolAcc = (LinkedHashMap<Integer, String[]>) acc[2];

                            JsonNode delta = extractDelta(chunk);
                            if (delta == null) return;

                            // 工具调用：按 index 累积 name + arguments
                            /*
                             小米响应：
                            "message": {
                                  "role": "assistant",
                                  "content": null, // 此时通常没有文本内容
                                  "tool_calls": [
                                    {
                                      "id": "call_abc123",
                                      "type": "function",
                                      "function": {
                                        "name": "get_current_weather",
                                        "arguments": "{\"location\": \"Boston, MA\", \"unit\": \"celsius\"}"
                                      }
                                    }
                                  ]
                                }
                            openai可能会拆成多个：
                            chunk 1:  delta: { tool_calls: [{ index: 0, id: "call_abc", type: "function",
                                   function: { name: "search_links", arguments: "" } }] }
                            chunk 2:  delta: { tool_calls: [{ index: 0, function: { arguments: "{\"qu" } }] }
                            chunk 3:  delta: { tool_calls: [{ index: 0, function: { arguments: "ery\": \"Re" } }] }
                            chunk 4:  delta: { tool_calls: [{ index: 0, function: { arguments: "act hooks\"}" } }] }
                            chunk 5:  delta: { content: "我帮你搜索一下..." }   ← 普通文本回复

                            chunk1: index=0, name="search", arguments=""     → slot[0]="search", slot[1]=""
                            chunk2: index=0, arguments="{\"query"            → slot[0]="search", slot[1]="{\"query"
                            chunk3: index=0, arguments="\":\"hello\"}"       → slot[0]="search", slot[1]="{\"query\":\"hello\"}"
                             */
                            JsonNode toolCalls = delta.get("tool_calls");
                            if (toolCalls != null && toolCalls.isArray()) {
                                for (JsonNode tc : toolCalls) {
                                    int index = tc.has("index") ? tc.get("index").asInt() : 0;
                                    JsonNode fnNode = tc.get("function");
                                    if (fnNode == null) continue;

                                    String[] slot = toolAcc.computeIfAbsent(index, k -> new String[]{"", ""});
                                    if (fnNode.has("name") && !fnNode.get("name").asText().isEmpty()) {
                                        slot[0] = fnNode.get("name").asText();
                                    }
                                    if (fnNode.has("arguments") && !fnNode.get("arguments").asText().isEmpty()) {
                                        slot[1] += fnNode.get("arguments").asText();
                                    }
                                }
                            }

                            // 累积 reasoning_content（思考过程）
                            if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                                String r = delta.get("reasoning_content").asText();
                                if (!r.isEmpty()) acc[0] = reasoning + r;
                            }

                            // 累积 content（最终回复）
                            if (delta.has("content") && !delta.get("content").isNull()) {
                                String c = delta.get("content").asText();
                                if (!c.isEmpty()) acc[1] = content + c;
                            }
                        })
                .flatMapMany(acc -> {
                    String reasoning = (String) acc[0];
                    String content = (String) acc[1];
                    @SuppressWarnings("unchecked")
                    LinkedHashMap<Integer, String[]> toolAcc = (LinkedHashMap<Integer, String[]>) acc[2];

                    log.info("AI 输出 - reasoning长度: {}, content长度: {}, toolCall数量: {}",
                            reasoning.length(), content.length(), toolAcc.size());

                    List<AiChunk> result = new ArrayList<>();

                    // 按 index 顺序输出所有工具调用
                    for (Map.Entry<Integer, String[]> entry : toolAcc.entrySet()) {
                        String fnName = entry.getValue()[0];
                        String fnArgs = entry.getValue()[1];
                        if (fnName.isEmpty()) {
                            log.warn("工具调用 index={} 缺少 function name，跳过", entry.getKey());
                            continue;
                        }
                        if (fnArgs.isEmpty()) fnArgs = "{}";
                        log.info("AI 请求调用工具 [{}]: {}({})", entry.getKey(), fnName, fnArgs);
                        result.add(AiChunk.toolCall(fnName, fnArgs));
                    }

                    // 只输出 content（模型的实际回复），不输出 reasoning
                    if (!content.isEmpty()) {
                        result.add(AiChunk.text(content));
                    } else if (result.isEmpty()) {
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
