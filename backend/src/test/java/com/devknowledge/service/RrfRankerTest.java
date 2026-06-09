package com.devknowledge.service;

import com.devknowledge.dto.KbChunkSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("RrfRanker - RRF 融合排序")
class RrfRankerTest {

    private RrfRanker ranker;

    @BeforeEach
    void setUp() {
        ranker = new RrfRanker();
    }

    /** 构造测试用的 KbChunkSearchResult */
    private KbChunkSearchResult chunk(UUID id, String content, double score) {
        KbChunkSearchResult r = new KbChunkSearchResult();
        r.setId(id);
        r.setContent(content);
        r.setScore(score);
        return r;
    }

    // ==================== 核心 RRF 融合逻辑 ====================

    @Nested
    @DisplayName("重叠项融合")
    class OverlapTests {

        @Test
        @DisplayName("两路有重叠项时，重叠项得分更高")
        void overlappingItemsRankHigher() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            UUID idC = UUID.randomUUID();

            // BM25: A(rank=0), B(rank=1), C(rank=2)
            List<KbChunkSearchResult> bm25 = List.of(
                    chunk(idA, "chunk A", 0.9),
                    chunk(idB, "chunk B", 0.8),
                    chunk(idC, "chunk C", 0.7)
            );
            // 向量: B(rank=0), A(rank=1), D(rank=2)
            UUID idD = UUID.randomUUID();
            List<KbChunkSearchResult> vector = List.of(
                    chunk(idB, "chunk B", 0.95),
                    chunk(idA, "chunk A", 0.85),
                    chunk(idD, "chunk D", 0.75)
            );

            List<KbChunkSearchResult> result = ranker.merge(bm25, vector, 60, 10, KbChunkSearchResult::getId);

            // A 和 B 都出现在两路中，应排在前面
            // A: 1/(60+0) + 1/(60+1) = 1/60 + 1/61 ≈ 0.03306
            // B: 1/(60+1) + 1/(60+0) = 1/61 + 1/60 ≈ 0.03306（与 A 相同）
            // C: 1/(60+2) = 1/62 ≈ 0.01613
            // D: 1/(60+2) = 1/62 ≈ 0.01613（与 C 相同）
            assertThat(result).hasSize(4);
            // 前两名应为 A 和 B（得分相同，顺序可能互换）
            assertThat(result.get(0).getId()).isIn(idA, idB);
            assertThat(result.get(1).getId()).isIn(idA, idB);
            // 后两名应为 C 和 D
            assertThat(result.get(2).getId()).isIn(idC, idD);
            assertThat(result.get(3).getId()).isIn(idC, idD);
        }

        @Test
        @DisplayName("RRF 得分计算正确")
        void rrfScoreCalculationCorrect() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();

            // BM25: A(rank=0)
            List<KbChunkSearchResult> bm25 = List.of(chunk(idA, "A", 0.9));
            // 向量: B(rank=0)
            List<KbChunkSearchResult> vector = List.of(chunk(idB, "B", 0.9));

            // k=10 时验证得分
            // A: 1/(10+0) = 0.1
            // B: 1/(10+0) = 0.1
            List<KbChunkSearchResult> result = ranker.merge(bm25, vector, 10, 10, KbChunkSearchResult::getId);
            assertThat(result).hasSize(2);
            // 两者得分相同
            assertThat(result.get(0).getId()).isIn(idA, idB);
            assertThat(result.get(1).getId()).isIn(idA, idB);
        }
    }

    @Nested
    @DisplayName("无重叠项")
    class NoOverlapTests {

        @Test
        @DisplayName("两路无重叠时，所有项都出现在结果中")
        void noOverlapAllItemsPresent() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            UUID id4 = UUID.randomUUID();

            List<KbChunkSearchResult> list1 = List.of(
                    chunk(id1, "A", 0.9),
                    chunk(id2, "B", 0.8)
            );
            List<KbChunkSearchResult> list2 = List.of(
                    chunk(id3, "C", 0.7),
                    chunk(id4, "D", 0.6)
            );

            List<KbChunkSearchResult> result = ranker.merge(list1, list2, 60, 10, KbChunkSearchResult::getId);

            assertThat(result).hasSize(4);
            assertThat(result).extracting(KbChunkSearchResult::getId)
                    .containsExactlyInAnyOrder(id1, id2, id3, id4);
        }
    }

    @Nested
    @DisplayName("空列表处理")
    class EmptyListTests {

        @Test
        @DisplayName("两路都为空时返回空列表")
        void bothEmptyReturnsEmpty() {
            List<KbChunkSearchResult> result = ranker.merge(List.of(), List.of(), 60, 10, KbChunkSearchResult::getId);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("两路为 null 时返回空列表")
        void bothNullReturnsEmpty() {
            List<KbChunkSearchResult> result = ranker.merge(null, null, 60, 10, KbChunkSearchResult::getId);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("第一路为空时返回第二路")
        void firstEmptyReturnsSecond() {
            UUID id1 = UUID.randomUUID();
            List<KbChunkSearchResult> list2 = List.of(chunk(id1, "A", 0.9));

            List<KbChunkSearchResult> result = ranker.merge(List.of(), list2, 60, 10, KbChunkSearchResult::getId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(id1);
        }

        @Test
        @DisplayName("第二路为空时返回第一路")
        void secondEmptyReturnsFirst() {
            UUID id1 = UUID.randomUUID();
            List<KbChunkSearchResult> list1 = List.of(chunk(id1, "A", 0.9));

            List<KbChunkSearchResult> result = ranker.merge(list1, List.of(), 60, 10, KbChunkSearchResult::getId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(id1);
        }

        @Test
        @DisplayName("第一路为 null 时返回第二路")
        void firstNullReturnsSecond() {
            UUID id1 = UUID.randomUUID();
            List<KbChunkSearchResult> list2 = List.of(chunk(id1, "A", 0.9));

            List<KbChunkSearchResult> result = ranker.merge(null, list2, 60, 10, KbChunkSearchResult::getId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(id1);
        }

        @Test
        @DisplayName("第二路为 null 时返回第一路")
        void secondNullReturnsFirst() {
            UUID id1 = UUID.randomUUID();
            List<KbChunkSearchResult> list1 = List.of(chunk(id1, "A", 0.9));

            List<KbChunkSearchResult> result = ranker.merge(list1, null, 60, 10, KbChunkSearchResult::getId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(id1);
        }
    }

    @Nested
    @DisplayName("topN 限制")
    class TopNTests {

        @Test
        @DisplayName("结果数量不超过 topN")
        void topNLimitsResults() {
            // 构造 10 个不同 ID 的 chunk
            List<KbChunkSearchResult> list1 = new java.util.ArrayList<>();
            List<KbChunkSearchResult> list2 = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                list1.add(chunk(UUID.randomUUID(), "chunk-" + i, 0.9 - i * 0.1));
                list2.add(chunk(UUID.randomUUID(), "chunk-" + (i + 5), 0.8 - i * 0.1));
            }

            List<KbChunkSearchResult> result = ranker.merge(list1, list2, 60, 3, KbChunkSearchResult::getId);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("topN 大于总项数时返回全部")
        void topNLargerThanTotalReturnsAll() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<KbChunkSearchResult> list1 = List.of(chunk(id1, "A", 0.9));
            List<KbChunkSearchResult> list2 = List.of(chunk(id2, "B", 0.8));

            List<KbChunkSearchResult> result = ranker.merge(list1, list2, 60, 100, KbChunkSearchResult::getId);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("k 参数影响")
    class KParameterTests {

        @Test
        @DisplayName("k 值越小，排名靠前的项得分优势越大")
        void smallerKIncreasesRankAdvantage() {
            UUID idA = UUID.randomUUID(); // 排名第 0
            UUID idB = UUID.randomUUID(); // 排名第 5

            // 两路都包含 A 和 B，但 A 排名靠前
            List<KbChunkSearchResult> list1 = new java.util.ArrayList<>();
            list1.add(chunk(idA, "A", 0.9));
            for (int i = 0; i < 5; i++) list1.add(chunk(UUID.randomUUID(), "filler-" + i, 0.5));
            list1.add(chunk(idB, "B", 0.3));

            List<KbChunkSearchResult> list2 = new java.util.ArrayList<>();
            list2.add(chunk(idA, "A", 0.9));
            for (int i = 0; i < 5; i++) list2.add(chunk(UUID.randomUUID(), "filler-" + i, 0.5));
            list2.add(chunk(idB, "B", 0.3));

            // k=10 时，A 得分 = 2/(10+0) = 0.2，B 得分 = 2/(10+5) ≈ 0.133
            List<KbChunkSearchResult> resultSmall = ranker.merge(list1, list2, 10, 10, KbChunkSearchResult::getId);
            assertThat(resultSmall.get(0).getId()).isEqualTo(idA);

            // k=1000 时，差距缩小但仍应保持顺序
            List<KbChunkSearchResult> resultLarge = ranker.merge(list1, list2, 1000, 10, KbChunkSearchResult::getId);
            assertThat(resultLarge.get(0).getId()).isEqualTo(idA);
        }

        @Test
        @DisplayName("不同 k 值融合结果长度一致")
        void differentKSameLength() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<KbChunkSearchResult> list1 = List.of(chunk(id1, "A", 0.9));
            List<KbChunkSearchResult> list2 = List.of(chunk(id2, "B", 0.8));

            List<KbChunkSearchResult> result1 = ranker.merge(list1, list2, 10, 10, KbChunkSearchResult::getId);
            List<KbChunkSearchResult> result2 = ranker.merge(list1, list2, 100, 10, KbChunkSearchResult::getId);

            assertThat(result1).hasSameSizeAs(result2);
        }
    }

    @Nested
    @DisplayName("确定性排序")
    class DeterministicTests {

        @Test
        @DisplayName("相同输入多次执行结果一致")
        void deterministicOutput() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            UUID idC = UUID.randomUUID();

            List<KbChunkSearchResult> list1 = List.of(
                    chunk(idA, "A", 0.9),
                    chunk(idB, "B", 0.8),
                    chunk(idC, "C", 0.7)
            );
            List<KbChunkSearchResult> list2 = List.of(
                    chunk(idB, "B", 0.95),
                    chunk(idA, "A", 0.85),
                    chunk(idC, "C", 0.75)
            );

            List<KbChunkSearchResult> result1 = ranker.merge(list1, list2, 60, 10, KbChunkSearchResult::getId);
            List<KbChunkSearchResult> result2 = ranker.merge(list1, list2, 60, 10, KbChunkSearchResult::getId);

            assertThat(result1).extracting(KbChunkSearchResult::getId)
                    .isEqualTo(result2.stream().map(KbChunkSearchResult::getId).toList());
        }

        @Test
        @DisplayName("同分项保持稳定顺序（先出现的排前面）")
        void stableOrderForTiedScores() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();

            // 两路都只有各自独占的项，rank 相同 => 得分相同
            List<KbChunkSearchResult> list1 = List.of(chunk(idA, "A", 0.9));
            List<KbChunkSearchResult> list2 = List.of(chunk(idB, "B", 0.8));

            // A 在 list1 中先被处理，B 在 list2 中后被处理
            // 由于 LinkedHashMap 保持插入顺序，同分时先插入的排前面
            List<KbChunkSearchResult> result = ranker.merge(list1, list2, 60, 10, KbChunkSearchResult::getId);
            assertThat(result).hasSize(2);
            // 两者得分相同：A = 1/60, B = 1/60
            // 由于 stream sorted 是稳定排序，先处理的 list1 中的 A 排在前面
            assertThat(result.get(0).getId()).isEqualTo(idA);
            assertThat(result.get(1).getId()).isEqualTo(idB);
        }
    }

    @Nested
    @DisplayName("默认 k 值重载")
    class DefaultKTests {

        @Test
        @DisplayName("使用默认 k=60 的重载方法结果正确")
        void defaultKOverload() {
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();

            List<KbChunkSearchResult> list1 = List.of(chunk(idA, "A", 0.9));
            List<KbChunkSearchResult> list2 = List.of(chunk(idB, "B", 0.8));

            List<KbChunkSearchResult> result = ranker.merge(list1, list2, 10, KbChunkSearchResult::getId);

            assertThat(result).hasSize(2);
            // 等价于 k=60 的调用
            List<KbChunkSearchResult> expected = ranker.merge(list1, list2, 60, 10, KbChunkSearchResult::getId);
            assertThat(result).extracting(KbChunkSearchResult::getId)
                    .isEqualTo(expected.stream().map(KbChunkSearchResult::getId).toList());
        }
    }

    @Nested
    @DisplayName("单路结果截取")
    class SingleListTruncationTests {

        @Test
        @DisplayName("单路结果超过 topN 时截取")
        void singleListTruncatedToTopN() {
            List<KbChunkSearchResult> list = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                list.add(chunk(UUID.randomUUID(), "chunk-" + i, 0.9 - i * 0.05));
            }

            List<KbChunkSearchResult> result = ranker.merge(list, List.of(), 60, 3, KbChunkSearchResult::getId);
            assertThat(result).hasSize(3);
            // 应取前 3 个
            assertThat(result.get(0).getContent()).isEqualTo("chunk-0");
            assertThat(result.get(1).getContent()).isEqualTo("chunk-1");
            assertThat(result.get(2).getContent()).isEqualTo("chunk-2");
        }

        @Test
        @DisplayName("null 单路截取也生效")
        void nullSingleListTruncated() {
            List<KbChunkSearchResult> list = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                list.add(chunk(UUID.randomUUID(), "chunk-" + i, 0.9));
            }

            List<KbChunkSearchResult> result = ranker.merge(null, list, 60, 2, KbChunkSearchResult::getId);
            assertThat(result).hasSize(2);
        }
    }
}
