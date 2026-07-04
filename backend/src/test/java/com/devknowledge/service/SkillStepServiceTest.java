package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.mapper.SkillStepMapper;
import com.devknowledge.model.SkillStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SkillStepService 单元测试
 * 覆盖：查询步骤、replaceSteps 先删后插事务、stepOrder 自动维护
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkillStepService - 步骤管理")
class SkillStepServiceTest {

    @Mock
    private SkillStepMapper skillStepMapper;

    @InjectMocks
    private SkillStepService skillStepService;

    private UUID skillId;

    @BeforeEach
    void setUp() {
        skillId = UUID.randomUUID();
    }

    // ==================== 查询步骤 ====================

    @Nested
    @DisplayName("查询步骤")
    class GetStepsTests {

        @Test
        @DisplayName("按 skillId 查询步骤，按 stepOrder 升序")
        void getStepsBySkillId() {
            List<SkillStep> steps = List.of(
                    createStep("步骤1", "action", 1),
                    createStep("步骤2", "validation", 2),
                    createStep("步骤3", "action", 3)
            );

            when(skillStepMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(steps);

            List<SkillStep> result = skillStepService.getStepsBySkillId(skillId);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getTitle()).isEqualTo("步骤1");
            assertThat(result.get(2).getStepOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("无步骤时返回空列表")
        void getStepsEmpty() {
            when(skillStepMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<SkillStep> result = skillStepService.getStepsBySkillId(skillId);

            assertThat(result).isEmpty();
        }
    }

    // ==================== replaceSteps 事务 ====================

    @Nested
    @DisplayName("replaceSteps - 先删后插")
    class ReplaceStepsTests {

        @Test
        @DisplayName("替换步骤：先删除旧步骤，再插入新步骤")
        void replaceStepsDeletesAndInserts() {
            List<SkillStep> newSteps = List.of(
                    createStep("新步骤1", "action", 0),
                    createStep("新步骤2", "decision", 0),
                    createStep("新步骤3", "validation", 0)
            );

            when(skillStepMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);
            when(skillStepMapper.insert(any(SkillStep.class))).thenReturn(1);

            skillStepService.replaceSteps(skillId, newSteps);

            // 验证删除旧步骤
            verify(skillStepMapper).delete(any(LambdaQueryWrapper.class));

            // 验证插入 3 个新步骤
            verify(skillStepMapper, times(3)).insert(any(SkillStep.class));
        }

        @Test
        @DisplayName("替换后 stepOrder 自动从 1 开始递增")
        void replaceStepsAutoOrdersStepOrder() {
            List<SkillStep> newSteps = List.of(
                    createStep("步骤A", "action", 99),   // 原 stepOrder=99
                    createStep("步骤B", "action", 0),    // 原 stepOrder=0
                    createStep("步骤C", "action", -1)    // 原 stepOrder=-1
            );

            when(skillStepMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
            when(skillStepMapper.insert(any(SkillStep.class))).thenReturn(1);

            skillStepService.replaceSteps(skillId, newSteps);

            // 捕获所有 insert 调用，验证 stepOrder
            ArgumentCaptor<SkillStep> captor = ArgumentCaptor.forClass(SkillStep.class);
            verify(skillStepMapper, times(3)).insert(captor.capture());

            List<SkillStep> inserted = captor.getAllValues();
            assertThat(inserted.get(0).getStepOrder()).isEqualTo(1);
            assertThat(inserted.get(1).getStepOrder()).isEqualTo(2);
            assertThat(inserted.get(2).getStepOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("替换时每个步骤获得新的 UUID 和 skillId")
        void replaceStepsGeneratesNewIds() {
            SkillStep step = createStep("步骤", "action", 1);
            UUID originalId = step.getId();

            when(skillStepMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
            when(skillStepMapper.insert(any(SkillStep.class))).thenReturn(1);

            skillStepService.replaceSteps(skillId, List.of(step));

            ArgumentCaptor<SkillStep> captor = ArgumentCaptor.forClass(SkillStep.class);
            verify(skillStepMapper).insert(captor.capture());

            SkillStep inserted = captor.getValue();
            // 新步骤应有新的 UUID
            assertThat(inserted.getId()).isNotEqualTo(originalId);
            assertThat(inserted.getSkillId()).isEqualTo(skillId);
        }

        @Test
        @DisplayName("空列表替换时只删除不插入")
        void replaceWithEmptyList() {
            when(skillStepMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(5);

            skillStepService.replaceSteps(skillId, List.of());

            verify(skillStepMapper).delete(any(LambdaQueryWrapper.class));
            verify(skillStepMapper, never()).insert(any(SkillStep.class));
        }

        @Test
        @DisplayName("替换步骤时设置 createdAt 和 updatedAt")
        void replaceStepsSetsTimestamps() {
            SkillStep step = createStep("步骤", "action", 1);
            step.setCreatedAt(null);
            step.setUpdatedAt(null);

            when(skillStepMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
            when(skillStepMapper.insert(any(SkillStep.class))).thenReturn(1);

            skillStepService.replaceSteps(skillId, List.of(step));

            ArgumentCaptor<SkillStep> captor = ArgumentCaptor.forClass(SkillStep.class);
            verify(skillStepMapper).insert(captor.capture());

            SkillStep inserted = captor.getValue();
            assertThat(inserted.getCreatedAt()).isNotNull();
            assertThat(inserted.getUpdatedAt()).isNotNull();
        }
    }

    // ==================== 辅助方法 ====================

    /** 创建测试用 SkillStep */
    private SkillStep createStep(String title, String stepType, int order) {
        SkillStep step = new SkillStep();
        step.setId(UUID.randomUUID());
        step.setSkillId(skillId);
        step.setTitle(title);
        step.setDescription("步骤描述");
        step.setStepType(stepType);
        step.setStepOrder(order);
        step.setCodeTemplate("template code");
        step.setExpectedOutput("expected output");
        step.setCreatedAt(Instant.now());
        step.setUpdatedAt(Instant.now());
        return step;
    }
}
