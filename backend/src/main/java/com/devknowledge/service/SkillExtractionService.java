package com.devknowledge.service;

import com.devknowledge.dto.ExtractSkillRequest;
import com.devknowledge.model.Skill;
import com.devknowledge.model.SkillStep;
import com.devknowledge.model.UserAiConfig;
import com.devknowledge.service.ai.AiChunk;
import com.devknowledge.service.ai.AiChunkType;
import com.devknowledge.service.ai.ReActAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 提取服务
 * 调用 ReActAgent 从用户描述中提取结构化 Skill
 */
@Service
@RequiredArgsConstructor
public class SkillExtractionService {

    private static final Logger log = LoggerFactory.getLogger(SkillExtractionService.class);

    private final ReActAgent reactAgent;
    private final AiConfigService aiConfigService;
    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    /**
     * 流式提取 Skill
     * maxIterations=1（无需工具调用，单轮生成即可）
     */
    public Flux<AiChunk> extractSkill(UUID userId, ExtractSkillRequest req) {
        // 匿名用户也需要能提取，但需要有 AI 配置
        if (userId == null) {
            return Flux.just(AiChunk.error("请先登录并配置 AI 服务商"));
        }

        UserAiConfig config = aiConfigService.getActiveConfigEntity(userId);
        if (config == null) {
            return Flux.just(AiChunk.error("请先在设置页配置 AI 服务商"));
        }

        if (req.getDescription() == null || req.getDescription().isBlank()) {
            return Flux.just(AiChunk.error("请输入 Skill 描述"));
        }

        String systemPrompt = buildExtractionPrompt(req);
        String userMessage = req.getDescription();

        return reactAgent.run(systemPrompt, userMessage, List.of(), Map.of(), config, 1);
    }

    /**
     * 提取完成后解析 JSON 并保存
     * Controller 在 SSE 流的 DONE 事件时调用
     */
    public Mono<Skill> parseAndSave(UUID userId, String rawOutput, ExtractSkillRequest req) {
        // 先在 boundedElastic 线程中同步解析 JSON
        return Mono.fromCallable(() -> parseAndEnrich(rawOutput, req))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .flatMap(parsed -> {
                    if (parsed == null) return Mono.empty();
                    return skillService.createSkill(userId, parsed, parsed.getSteps());
                });
    }

    /**
     * 解析 AI 输出并补充请求中的额外字段（同步方法）
     */
    private Skill parseAndEnrich(String rawOutput, ExtractSkillRequest req) {
        Skill parsed = parseSkillJson(rawOutput);
        if (parsed == null) {
            log.warn("Skill JSON 解析失败，原始输出长度: {}", rawOutput.length());
            return null;
        }
        // 设置请求中的额外字段
        if (req.getCategory() != null) parsed.setCategory(req.getCategory());
        if (req.getFrameworkId() != null && !req.getFrameworkId().isBlank()) {
            parsed.setFrameworkId(UUID.fromString(req.getFrameworkId()));
        }
        return parsed;
    }

    /**
     * 构建 Skill 提取 System Prompt
     */
    private String buildExtractionPrompt(ExtractSkillRequest req) {
        return "你是一个 Skill 提取助手。用户会描述一段工作流程，你需要从中提取结构化的 Skill。\n\n" +
            "请严格输出以下 JSON 格式（不要包含 markdown 代码围栏）：\n" +
            "{\n" +
            "  \"name\": \"Skill 名称\",\n" +
            "  \"description\": \"Skill 描述\",\n" +
            "  \"triggerDescription\": \"触发条件描述\",\n" +
            "  \"steps\": [\n" +
            "    {\n" +
            "      \"title\": \"步骤标题\",\n" +
            "      \"description\": \"步骤详细描述\",\n" +
            "      \"stepType\": \"action|decision|validation|reference\",\n" +
            "      \"codeTemplate\": \"代码模板（如适用）\",\n" +
            "      \"expectedOutput\": \"预期输出（如适用）\",\n" +
            "      \"notes\": \"补充说明（可选）\"\n" +
            "    }\n" +
            "  ]\n" +
            "}\n\n" +
            "要求：\n" +
            "1. steps 至少包含 1 个步骤\n" +
            "2. stepType 必须是 action/decision/validation/reference 之一\n" +
            "3. 为每个步骤生成合理的 codeTemplate 和 expectedOutput\n" +
            "4. 输出纯 JSON，不要包含任何其他文本";
    }

    /**
     * 从 AI 输出中解析 Skill JSON
     * 容错处理：AI 输出可能包含 markdown 代码围栏或前后多余文本
     */
    private Skill parseSkillJson(String rawOutput) {
        // 1. 尝试直接解析
        try {
            return objectMapper.readValue(rawOutput, Skill.class);
        } catch (Exception ignored) {
            log.debug("直接 JSON 解析失败，尝试提取 JSON 块");
        }

        // 2. 用正则提取 ```json ... ``` 或 { ... } 块
        Pattern jsonPattern = Pattern.compile("\\{[\\s\\S]*\"steps\"[\\s\\S]*\\}");
        Matcher matcher = jsonPattern.matcher(rawOutput);
        if (matcher.find()) {
            try {
                return objectMapper.readValue(matcher.group(), Skill.class);
            } catch (Exception e) {
                log.warn("提取的 JSON 块解析失败: {}", e.getMessage());
            }
        }

        // 3. 尝试提取更宽松的 JSON 块（不含 steps 关键词的情况）
        Pattern loosePattern = Pattern.compile("\\{[\\s\\S]*\"name\"[\\s\\S]*\\}");
        Matcher looseMatcher = loosePattern.matcher(rawOutput);
        if (looseMatcher.find()) {
            try {
                return objectMapper.readValue(looseMatcher.group(), Skill.class);
            } catch (Exception e) {
                log.warn("宽松 JSON 块解析失败: {}", e.getMessage());
            }
        }

        return null;
    }
}
