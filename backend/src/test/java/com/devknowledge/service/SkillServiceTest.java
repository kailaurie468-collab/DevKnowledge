package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devknowledge.dto.SkillUpdateRequest;
import com.devknowledge.mapper.SkillMapper;
import com.devknowledge.model.Skill;
import com.devknowledge.model.SkillStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SkillService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkillService - Skill CRUD + 导出")
class SkillServiceTest {

    @Mock
    private SkillMapper skillMapper;
    @Mock
    private SkillStepService skillStepService;

    @InjectMocks
    private SkillService skillService;

    private UUID userId;
    private UUID skillId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        skillId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("创建 Skill")
    class CreateSkillTests {

        @Test
        @DisplayName("创建 Skill + Steps 联动写入成功")
        void createSkillWithSteps() {
            Skill skill = createSkill("React 组件开发", "frontend");
            List<SkillStep> steps = List.of(
                    createStep("定义类型", "action", 1),
                    createStep("编写组件", "action", 2)
            );

            when(skillStepService.getStepsBySkillId(any(UUID.class))).thenReturn(List.of());

            Skill result = skillService.createSkill(userId, skill, steps).block();

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("React 组件开发");
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getVersion()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("查询 Skill")
    class QuerySkillTests {

        @Test
        @DisplayName("按 ID 查询 Skill（含 Steps）")
        void getSkillById() {
            Skill skill = createSkill("测试", "backend");
            skill.setId(skillId);
            skill.setUserId(userId);

            List<SkillStep> steps = List.of(createStep("步骤1", "action", 1));

            when(skillMapper.selectById(skillId)).thenReturn(skill);
            when(skillStepService.getStepsBySkillId(skillId)).thenReturn(steps);

            Skill result = skillService.getSkillById(skillId, userId).block();

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("测试");
        }

        @Test
        @DisplayName("查询不存在的 Skill 返回空")
        void getNonexistentSkill() {
            when(skillMapper.selectById(skillId)).thenReturn(null);

            Skill result = skillService.getSkillById(skillId, userId).block();

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("非 owner 查询时返回 null")
        void getSkillByWrongUser() {
            Skill skill = createSkill("测试", "backend");
            skill.setId(skillId);
            skill.setUserId(userId);

            when(skillMapper.selectById(skillId)).thenReturn(skill);

            UUID wrongUser = UUID.randomUUID();
            Skill result = skillService.getSkillById(skillId, wrongUser).block();

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("分页查询用户 Skills")
        void getUserSkillsWithPaging() {
            Page<Skill> mockPage = new Page<>(1, 10);
            List<Skill> skills = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Skill s = createSkill("skill-" + i, "frontend");
                s.setUserId(userId);
                skills.add(s);
            }
            mockPage.setRecords(skills);
            mockPage.setTotal(5);

            when(skillMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<Skill> result = skillService.getUserSkills(userId, null, null, 1, 10).block();

            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("更新 Skill")
    class UpdateSkillTests {

        @Test
        @DisplayName("更新 Skill 后 version 自增")
        void updateIncrementsVersion() {
            Skill existing = createSkill("旧名称", "frontend");
            existing.setId(skillId);
            existing.setUserId(userId);
            existing.setVersion(1);

            when(skillMapper.selectById(skillId)).thenReturn(existing);
            when(skillStepService.getStepsBySkillId(skillId)).thenReturn(List.of());

            SkillUpdateRequest req = new SkillUpdateRequest();
            req.setName("新名称");
            req.setDescription("新描述");

            Skill result = skillService.updateSkill(skillId, userId, req).block();

            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("非 owner 更新被拒绝")
        void updateByWrongUser() {
            Skill existing = createSkill("测试", "frontend");
            existing.setId(skillId);
            existing.setUserId(userId);

            when(skillMapper.selectById(skillId)).thenReturn(existing);

            UUID wrongUser = UUID.randomUUID();
            SkillUpdateRequest req = new SkillUpdateRequest();
            req.setName("恶意修改");

            assertThatThrownBy(() -> skillService.updateSkill(skillId, wrongUser, req).block())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("无权");
        }
    }

    @Nested
    @DisplayName("删除 Skill（软删除）")
    class DeleteSkillTests {

        @Test
        @DisplayName("软删除标记 isDeleted=true")
        void softDelete() {
            Skill existing = createSkill("测试", "frontend");
            existing.setId(skillId);
            existing.setUserId(userId);

            when(skillMapper.selectById(skillId)).thenReturn(existing);

            // 不抛异常即为成功
            skillService.deleteSkill(skillId, userId).block();
        }

        @Test
        @DisplayName("非 owner 删除被拒绝")
        void deleteByWrongUser() {
            Skill existing = createSkill("测试", "frontend");
            existing.setId(skillId);
            existing.setUserId(userId);

            when(skillMapper.selectById(skillId)).thenReturn(existing);

            UUID wrongUser = UUID.randomUUID();
            assertThatThrownBy(() -> skillService.deleteSkill(skillId, wrongUser).block())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("无权");
        }
    }

    @Nested
    @DisplayName("Markdown 导出")
    class ExportTests {

        @Test
        @DisplayName("导出 Markdown 包含 Skill 名称和描述")
        void exportContainsNameAndDescription() {
            Skill skill = createSkill("React 组件开发", "frontend");
            skill.setId(skillId);
            skill.setUserId(userId);
            skill.setDescription("React 组件开发标准流程");
            skill.setTriggerDescription("需要创建新的 React 组件时");

            List<SkillStep> steps = List.of(
                    createStep("定义 Props 类型", "action", 1),
                    createStep("编写组件", "action", 2)
            );

            when(skillMapper.selectById(skillId)).thenReturn(skill);
            when(skillStepService.getStepsBySkillId(skillId)).thenReturn(steps);

            String markdown = skillService.exportToMarkdown(skillId, userId).block();

            assertThat(markdown).contains("# React 组件开发");
            assertThat(markdown).contains("React 组件开发标准流程");
            assertThat(markdown).contains("定义 Props 类型");
        }

        @Test
        @DisplayName("非 owner 导出被拒绝")
        void exportByWrongUser() {
            Skill skill = createSkill("测试", "backend");
            skill.setId(skillId);
            skill.setUserId(userId);

            when(skillMapper.selectById(skillId)).thenReturn(skill);

            UUID wrongUser = UUID.randomUUID();
            assertThatThrownBy(() -> skillService.exportToMarkdown(skillId, wrongUser).block())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("无权");
        }
    }

    private Skill createSkill(String name, String category) {
        Skill skill = new Skill();
        skill.setId(UUID.randomUUID());
        skill.setUserId(userId);
        skill.setName(name);
        skill.setDescription("测试描述");
        skill.setCategory(category);
        skill.setVersion(1);
        skill.setIsPublic(false);
        skill.setIsDeleted(false);
        skill.setCreatedAt(Instant.now());
        skill.setUpdatedAt(Instant.now());
        return skill;
    }

    private SkillStep createStep(String title, String stepType, int order) {
        SkillStep step = new SkillStep();
        step.setId(UUID.randomUUID());
        step.setSkillId(skillId);
        step.setTitle(title);
        step.setDescription("步骤描述");
        step.setStepType(stepType);
        step.setStepOrder(order);
        step.setCreatedAt(Instant.now());
        step.setUpdatedAt(Instant.now());
        return step;
    }
}
