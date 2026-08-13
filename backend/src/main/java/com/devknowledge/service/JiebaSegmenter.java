package com.devknowledge.service;

import com.huaban.analysis.jieba.JiebaSegmenter.SegMode;
import com.huaban.analysis.jieba.SegToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Jieba 中文分词工具类
 * 用于 BM25 全文检索的文本预处理：分词 + 去停用词
 */
@Component
public class JiebaSegmenter {

    private final com.huaban.analysis.jieba.JiebaSegmenter segmenter
            = new com.huaban.analysis.jieba.JiebaSegmenter();

    /** token 至少含一个中文字符、字母或数字才保留（过滤纯标点/符号） */
    private static final Pattern HAS_CONTENT = Pattern.compile("[\\p{IsHan}a-zA-Z0-9]");
    /** 单个中文字符（语义弱，检索噪声大） */
    private static final Pattern SINGLE_CHAR_CJK = Pattern.compile("^\\p{IsHan}$");

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
        return cleanTokens(text).stream().collect(Collectors.joining(" "));
    }

    /**
     * 构建 AND 语义的 tsquery 表达式：分词后用 & 连接
     * 要求所有词项都命中，召回率低，仅用于精确匹配场景（检索链路请用 {@link #buildOrTsQuery}）
     *
     * @param text 查询文本
     * @return tsquery 表达式，如 "知识 & 图谱 & 检索"
     */
    public String buildTsQuery(String text) {
        if (text == null || text.isBlank()) return "";
        return cleanTokens(text).stream().collect(Collectors.joining(" & "));
    }

    /**
     * 构建 OR 语义的 tsquery 表达式：分词后用 | 连接，供 to_tsquery() 使用
     * <p>
     * 必须与入库侧走同一套 cleanTokens 管道：入库时 tsv 存的是 Jieba 切分后的词项，
     * 若查询侧直接把原文交给 PostgreSQL，simple 配置不切中文，
     * 整串连续汉字会变成单个 lexeme（如 "导航库"），永远匹配不上 tsv 里的 "导航"、"库"。
     * <p>
     * 用 OR 而非 AND：部分命中即可召回，精度交给后续 Reranker 精排。
     *
     * @param text 查询文本
     * @return tsquery 表达式，如 "'知识' | '图谱' | '检索'"；无有效词项时返回空串
     */
    public String buildOrTsQuery(String text) {
        if (text == null || text.isBlank()) return "";
        return cleanTokens(text).stream()
                .map(JiebaSegmenter::quoteLexeme)
                .collect(Collectors.joining(" | "));
    }

    /**
     * 将词项包装成 tsquery 字面量：单引号包裹 + 内部反斜杠和单引号加倍
     * 避免词项中的 & | ! ( ) : * 等字符被 to_tsquery 当作运算符解析
     */
    private static String quoteLexeme(String token) {
        return "'" + token.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    /**
     * 公共 token 清洗管道：
     * 1. Jieba SEARCH 模式分词
     * 2. 去除前后标点符号
     * 3. 过滤纯标点/符号 token（要求至少含一个汉字、字母或数字）
     * 4. 过滤单字中文（语义弱、噪声大，如 "一"、"种"、"个"）
     * 5. 过滤停用词
     * 6. 限制 token 长度（防止超长乱码）
     */
    private List<String> cleanTokens(String text) {
        List<SegToken> tokens = segmenter.process(text, SegMode.SEARCH);
        return tokens.stream()
                .map(token -> token.word)
                .filter(word -> word != null && !word.isBlank())
                // 去除 token 前后的标点符号（如 Jieba 可能输出 "框架," 或 ".NET"）
                .map(JiebaSegmenter::stripPunctuation)
                .filter(word -> !word.isEmpty())
                // 必须含至少一个汉字、字母或数字（过滤 "###" "(" ";" 等纯符号）
                .filter(word -> HAS_CONTENT.matcher(word).find())
                // 过滤单字中文（"一" "种" "个" 等语义弱词）
                .filter(word -> !SINGLE_CHAR_CJK.matcher(word).matches())
                // 停用词过滤
                .filter(word -> !STOP_WORDS.contains(word.toLowerCase()))
                // 限制 token 长度，防止超长乱码
                .filter(word -> word.length() <= 50)
                .toList();
    }

    /**
     * 去除字符串首尾的标点符号和特殊字符
     * 保留中间的（如 "C++" → "C++", ".NET" → "NET", "v2.1" → "v2.1"）
     */
    private static String stripPunctuation(String word) {
        // 去除开头的标点
        int start = 0;
        while (start < word.length() && isPunctuation(word.charAt(start))) {
            start++;
        }
        // 去除结尾的标点
        int end = word.length();
        while (end > start && isPunctuation(word.charAt(end - 1))) {
            end--;
        }
        return word.substring(start, end);
    }

    /**
     * 判断字符是否为标点或特殊符号
     */
    private static boolean isPunctuation(char c) {
        if (Character.isLetterOrDigit(c)) return false;
        if (c == '+' || c == '#' || c == '_') return false; // 保留编程常见符号（C++ / C# / snake_case）
        return true;
    }
}
