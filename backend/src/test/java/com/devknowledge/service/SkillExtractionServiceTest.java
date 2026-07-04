package com.devknowledge.service;

import com.devknowledge.dto.ExtractSkillRequest;
import com.devknowledge.model.Skill;
import com.devknowledge.model.SkillStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SkillExtractionService 单元测试
 * 覆盖：JSON 解析容错（纯 JSON / markdown 围栏 / 前后噪音）、System Prompt 构造、保存逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkillExtractionService - AI 提取")
class SkillExtractionServiceTest {

    @Mock
    private SkillService skillService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SkillExtractionService extractionService;

    private UUID userId;
    private final ObjectMapper realMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    // ==================== JSON 解析容错 ====================

    @Nested
    @DisplayName("JSON 解析容错")
    class ParseJsonTests {

        @Test
        @DisplayName("纯 JSON 直接解析成功")
        void parsePureJson() throws Exception {
            String json = """
                    {
                      "name": "React 组件开发",
                      "description": "标准流程",
                      "triggerDescription": "创建组件时",
                      "steps": [
                        {
                          "title": "定义类型",
                          "description": "定义 Props 接口",
                          "stepType": "action",
                          "codeTemplate": "interface Props {}",
                          "expectedOutput": "类型定义完成"
                        }
                      ]
                    }
                    """;

            Skill result = invokeParseSkillJson(json);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("React 组件开发");
            assertThat(result.getSteps()).hasSize(1);
            assertThat(result.getSteps().get(0).getStepType()).isEqualTo("action");
        }

        @Test
        @DisplayName("AI 输出包含 markdown 代码围栏时自动提取 JSON")
        void parseMarkdownFencedJson() throws Exception {
            String raw = """
                    这是从描述中提取的 Skill：

                    ```json
                    {
                      "name": "API 测试编写",
                      "description": "编写 API 接口测试",
                      "steps": [
                        {
                          "title": "编写测试用例",
                          "stepType": "action"
                        }
                      ]
                    }
                    ```

                    以上是提取结果。
                    """;

            Skill result = invokeParseSkillJson(raw);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("API 测试编写");
        }

        @Test
        @DisplayName("AI 输出前后有噪音文本时仍能解析")
        void parseWithNoiseText() throws Exception {
            String raw = """
                    好的，我来分析这段工作流程...
                    这是一个关于数据库迁移的 Skill。

                    {"name":"数据库迁移","description":"Schema 变更流程","steps":[{"title":"编写迁移脚本","stepType":"action"}]}

                    希望这个 Skill 对你有帮助！
                    """;

            Skill result = invokeParseSkillJson(raw);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("数据库迁移");
        }

        @Test
        @DisplayName("非法 JSON 返回 null")
        void parseInvalidJson() throws Exception {
            Skill result = invokeParseSkillJson("这不是 JSON，也不是包含 JSON 的文本");

            assertThat(result).isNull();
        }
    }

    // ==================== parseAndSave ====================

    @Nested
    @DisplayName("parseAndSave - 解析并保存")
    class ParseAndSaveTests {

        @Test
        @DisplayName("解析成功后调用 skillService.createSkill 保存")
        void saveAfterParse() throws Exception {
            String json = """
                    {"name":"测试","description":"desc","steps":[{"title":"步骤1","stepType":"action"}]}
                    """;

            ExtractSkillRequest req = new ExtractSkillRequest();
            req.setDescription("创建一个测试");
            req.setCategory("frontend");
            req.setFrameworkId(null);

            Skill savedSkill = new Skill();
            savedSkill.setId(UUID.randomUUID());
            savedSkill.setName("测试");

            Skill parsedSkill = new Skill();
            parsedSkill.setName("测试");
            parsedSkill.setSteps(List.of());

            when(objectMapper.readValue(anyString(), eq(Skill.class))).thenReturn(parsedSkill);
            when(skillService.createSkill(any(), any(), any())).thenReturn(
                    reactor.core.publisher.Mono.just(savedSkill));

            Skill result = extractionService.parseAndSave(userId, json, req).block();

            assertThat(result).isNotNull();
            verify(skillService).createSkill(eq(userId), any(Skill.class), any());
        }

        @Test
        @DisplayName("解析失败时不保存，返回空")
        void noSaveOnParseFailure() throws Exception {
            ExtractSkillRequest req = new ExtractSkillRequest();
            req.setDescription("无效描述");

            when(objectMapper.readValue(anyString(), eq(Skill.class)))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("parse error") {});

            Skill result = extractionService.parseAndSave(userId, "invalid", req).block();

            assertThat(result).isNull();
            verify(skillService, never()).createSkill(any(), any(), any());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过反射调用私有方法 parseSkillJson
     */
    private Skill invokeParseSkillJson(String rawOutput) throws Exception {
        SkillExtractionService realService = new SkillExtractionService(
                null, null, skillService, realMapper);
        Method method = SkillExtractionService.class.getDeclaredMethod("parseSkillJson", String.class);
        method.setAccessible(true);
        return (Skill) method.invoke(realService, rawOutput);
    }
}
