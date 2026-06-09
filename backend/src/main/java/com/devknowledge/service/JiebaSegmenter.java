package com.devknowledge.service;

import com.huaban.analysis.jieba.JiebaSegmenter.SegMode;
import com.huaban.analysis.jieba.SegToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Jieba 中文分词工具类
 * 用于 BM25 全文检索的文本预处理：分词 + 去停用词
 */
@Component
public class JiebaSegmenter {

    private final com.huaban.analysis.jieba.JiebaSegmenter segmenter
            = new com.huaban.analysis.jieba.JiebaSegmenter();

    /** 中英文停用词表 */
    private static final Set<String> STOP_WORDS = Set.of(
            // 中文停用词
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "里", "为", "什么", "怎么", "如何",
            "可以", "以", "能", "对", "但", "而", "或", "与", "及", "等", "之", "中",
            "把", "被", "让", "给", "从", "向", "又", "已", "只", "更", "最", "将",
            "其", "这个", "那个", "这些", "那些", "一些", "每个", "所有", "其他",
            "因为", "所以", "如果", "虽然", "但是", "然后", "因此", "不过", "而且",
            // 英文停用词
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "shall", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "and", "but", "or",
            "not", "no", "nor", "so", "if", "then", "than", "too", "very",
            "it", "its", "this", "that", "these", "those", "i", "me", "my",
            "we", "our", "you", "your", "he", "him", "his", "she", "her",
            "they", "them", "their", "what", "which", "who", "whom", "where",
            "when", "why", "how", "all", "each", "every", "both", "few",
            "more", "most", "other", "some", "such", "only", "own", "same"
    );

    /**
     * 分词并去停用词，返回空格分隔的 token 串
     * 用于存入 PostgreSQL tsvector 列（直接作为 lexeme 串）
     *
     * @param text 原始文本
     * @return 空格分隔的 token，如 "知识 图谱 检索"
     */
    public String segment(String text) {
        if (text == null || text.isBlank()) return "";
        // Jieba 分词（SEARCH 模式更适合检索场景）
        List<SegToken> tokens = segmenter.process(text, SegMode.SEARCH);
        return tokens.stream()
                .map(token -> token.word)
                .filter(word -> word != null && !word.isBlank())
                .filter(word -> !STOP_WORDS.contains(word.toLowerCase()))
                .collect(Collectors.joining(" "));
    }

    /**
     * 构建 tsquery 表达式：分词后用 & 连接
     * 用于 PostgreSQL 全文检索的 @@ 匹配运算符
     *
     * @param text 查询文本
     * @return tsquery 表达式，如 "知识 & 图谱 & 检索"
     */
    public String buildTsQuery(String text) {
        if (text == null || text.isBlank()) return "";
        List<SegToken> tokens = segmenter.process(text, SegMode.SEARCH);
        return tokens.stream()
                .map(token -> token.word)
                .filter(word -> word != null && !word.isBlank())
                .filter(word -> !STOP_WORDS.contains(word.toLowerCase()))
                .collect(Collectors.joining(" & "));
    }
}
