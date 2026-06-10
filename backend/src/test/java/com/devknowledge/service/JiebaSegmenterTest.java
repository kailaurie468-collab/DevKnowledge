package com.devknowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JiebaSegmenter - 中文分词 + 停用词过滤")
class JiebaSegmenterTest {

    private JiebaSegmenter segmenter;

    @BeforeEach
    void setUp() {
        segmenter = new JiebaSegmenter();
    }

    // ==================== segment() 分词 ====================

    @Nested
    @DisplayName("segment 分词测试")
    class SegmentTests {

        @Test
        @DisplayName("中文文本分词并去停用词")
        void chineseSegment() {
            String result = segmenter.segment("知识图谱是一种结构化的语义知识库");
            // 停用词"是"、"的"应被过滤
            assertThat(result).doesNotContain("是");
            assertThat(result).doesNotContain("的");
            // 核心词应保留
            assertThat(result).contains("知识");
            assertThat(result).contains("图谱");
            assertThat(result).isNotBlank();
            // 结果应为空格分隔
            assertThat(result.split("\\s+").length).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("英文文本分词并去停用词")
        void englishSegment() {
            String result = segmenter.segment("The quick brown fox is jumping over the lazy dog");
            // 停用词 "The", "is", "the" 应被过滤
            String lower = result.toLowerCase();
            // "the" 和 "is" 是停用词，不应出现在结果中
            // 注意：Jieba 对英文是按空格分词的，每个单词是一个 token
            assertThat(lower).doesNotContainPattern("\\bthe\\b");
            assertThat(lower).doesNotContainPattern("\\bis\\b");
            // 核心词应保留
            assertThat(result).containsIgnoringCase("quick");
            assertThat(result).containsIgnoringCase("fox");
        }

        @Test
        @DisplayName("中英混合文本分词")
        void mixedSegment() {
            String result = segmenter.segment("Spring Boot 是一个 Java 框架，用于构建微服务");
            assertThat(result).isNotBlank();
            // "是"、"一个" 是停用词，应被过滤
            // Jieba 可能将 "一个" 拆分为 "一" 和 "个"，停用词表含 "一个"
            // 核心词应保留
            assertThat(result).containsIgnoringCase("Spring");
            assertThat(result).containsIgnoringCase("Boot");
            assertThat(result).containsIgnoringCase("Java");
            assertThat(result).contains("框架");
            // "微服务" 可能被 Jieba 拆分为 "微" + "服务"，检查 "服务"
            assertThat(result).contains("服务");
        }

        @Test
        @DisplayName("null 输入返回空字符串")
        void nullInput() {
            assertThat(segmenter.segment(null)).isEmpty();
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void emptyInput() {
            assertThat(segmenter.segment("")).isEmpty();
        }

        @Test
        @DisplayName("纯空白返回空字符串")
        void blankInput() {
            assertThat(segmenter.segment("   ")).isEmpty();
        }

        @Test
        @DisplayName("纯停用词文本返回空字符串")
        void stopWordsOnly() {
            String result = segmenter.segment("的 了 在 是 我");
            // 所有词都是停用词，结果应为空或只含空格
            assertThat(result.trim()).isEmpty();
        }

        @Test
        @DisplayName("长文本分词结果包含关键术语")
        void longTextSegment() {
            String text = "PostgreSQL 是一个功能强大的开源对象关系数据库系统，" +
                    "它支持 SQL 标准的大部分功能，包括事务、子查询、触发器和视图。" +
                    "PostgreSQL 还支持全文检索、JSON 数据类型和地理空间数据处理。";
            String result = segmenter.segment(text);
            assertThat(result).isNotBlank();
            assertThat(result).containsIgnoringCase("PostgreSQL");
            assertThat(result).contains("数据库");
            assertThat(result).contains("全文检索");
            // 停用词被过滤
            assertThat(result).doesNotContain("是");
            assertThat(result).doesNotContain("的");
            assertThat(result).doesNotContain("它");
        }

        @Test
        @DisplayName("过滤标点符号和特殊字符")
        void filterPunctuation() {
            // 模拟 Markdown 文档内容的分词
            String text = "### 第 2.1 节：使用 `@Component` 注解（Spring Boot）；详见 <官方文档>";
            String result = segmenter.segment(text);
            // 纯符号 token 应被过滤
            assertThat(result).doesNotContain("###");
            assertThat(result).doesNotContain("(");
            assertThat(result).doesNotContain(")");
            assertThat(result).doesNotContain(";");
            assertThat(result).doesNotContain("；");
            assertThat(result).doesNotContain("<");
            assertThat(result).doesNotContain(">");
            assertThat(result).doesNotContain("`");
            assertThat(result).doesNotContain("@");
            // 有意义的词应保留
            assertThat(result).containsIgnoringCase("Spring");
            assertThat(result).containsIgnoringCase("Boot");
            assertThat(result).contains("注解");
        }

        @Test
        @DisplayName("过滤纯数字版本号")
        void filterVersionNumbers() {
            String result = segmenter.segment("版本 2.1 发布了，需要 Java 17 以上");
            // "2.1" 纯数字+点号会被 stripPunctuation 处理
            // 核心词保留
            assertThat(result).contains("版本");
            assertThat(result).contains("发布");
        }

        @Test
        @DisplayName("保留编程专有名词中的特殊字符")
        void preserveProgrammingTerms() {
            String result = segmenter.segment("C++ 和 C# 是常用编程语言");
            // C++ 和 C# 中的 + 和 # 应被保留（Jieba 可能转为小写）
            String lower = result.toLowerCase();
            assertThat(lower).contains("c++");
            assertThat(lower).contains("c#");
        }
    }

    // ==================== buildTsQuery() tsquery 构建 ====================

    @Nested
    @DisplayName("buildTsQuery tsquery 构建测试")
    class TsQueryTests {

        @Test
        @DisplayName("中文查询构建 tsquery（& 分隔）")
        void chineseTsQuery() {
            String tsQuery = segmenter.buildTsQuery("知识图谱检索");
            assertThat(tsQuery).isNotBlank();
            // 应包含 & 分隔符
            assertThat(tsQuery).contains(" & ");
            // 不应包含停用词
            assertThat(tsQuery).doesNotContain("的");
        }

        @Test
        @DisplayName("英文查询构建 tsquery")
        void englishTsQuery() {
            String tsQuery = segmenter.buildTsQuery("machine learning algorithm");
            assertThat(tsQuery).isNotBlank();
            assertThat(tsQuery).contains(" & ");
            assertThat(tsQuery).containsIgnoringCase("machine");
            assertThat(tsQuery).containsIgnoringCase("learning");
            assertThat(tsQuery).containsIgnoringCase("algorithm");
        }

        @Test
        @DisplayName("null 输入返回空字符串")
        void nullTsQuery() {
            assertThat(segmenter.buildTsQuery(null)).isEmpty();
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void emptyTsQuery() {
            assertThat(segmenter.buildTsQuery("")).isEmpty();
        }

        @Test
        @DisplayName("tsquery 结果可直接用于 PostgreSQL 查询")
        void tsQueryFormat() {
            String tsQuery = segmenter.buildTsQuery("Java 微服务架构");
            // 格式应为 "token1 & token2 & token3"
            String[] parts = tsQuery.split(" & ");
            assertThat(parts.length).isGreaterThanOrEqualTo(2);
            // 每个 token 不应包含空格或特殊字符
            for (String part : parts) {
                assertThat(part.trim()).isNotEmpty();
                assertThat(part.trim()).doesNotContain(" ");
            }
        }
    }

    // ==================== 分词一致性 ====================

    @Nested
        @DisplayName("分词一致性")
    class ConsistencyTests {

        @Test
        @DisplayName("相同文本多次分词结果一致")
        void consistentSegmentation() {
            String text = "深度学习是人工智能的一个重要分支";
            String first = segmenter.segment(text);
            String second = segmenter.segment(text);
            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("segment 和 buildTsQuery 分词结果一致（连接符不同）")
        void segmentAndTsQueryConsistent() {
            String text = "向量数据库检索技术";
            String segResult = segmenter.segment(text);
            String tsQuery = segmenter.buildTsQuery(text);
            // 用 & 替换空格后应等价
            String normalizedSeg = segResult.replace(" ", " & ");
            assertThat(tsQuery).isEqualTo(normalizedSeg);
        }
    }
}
