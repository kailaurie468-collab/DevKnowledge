package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.mapper.WikiIndexMapper;
import com.devknowledge.model.WikiIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Wiki 检索服务
 * 为 Demo 生成提供基于索引的 Wiki 上下文检索（改进版：多维度评分排序）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiRetrievalService {

    private final WikiIndexMapper wikiIndexMapper;
    private final WikiFileService wikiFileService;

    // 中文/英文常见停用词，不参与检索
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "和", "有", "不", "这", "我", "你", "他", "她", "它",
            "什么", "如何", "怎么", "为什么", "请", "帮", "想", "要", "可以", "能",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "to", "of", "in", "for", "on", "with",
            "at", "by", "from", "as", "into", "about", "how", "what", "why", "when"
    );

    /**
     * 根据查询检索相关 wiki 页面上下文（多维度评分）
     */
    public Mono<String> retrieveContext(UUID userId, String query) {
        return Mono.fromCallable(() -> {
            // 获取所有索引
            List<WikiIndex> allIndex = wikiIndexMapper.selectList(
                    new LambdaQueryWrapper<WikiIndex>()
                            .eq(WikiIndex::getUserId, userId));

            if (allIndex.isEmpty()) {
                return new ArrayList<WikiIndex>();
            }

            // 提取并过滤关键词
            String queryLower = query.toLowerCase();
            List<String> keywords = Arrays.stream(queryLower.split("[\\s,;，；、]+"))
                    .map(k -> k.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5-]", ""))
                    .filter(k -> k.length() > 1 && !STOP_WORDS.contains(k))
                    .distinct()
                    .collect(Collectors.toList());

            // 多维度评分排序
            List<ScoredIndex> scored = allIndex.stream()
                    .map(idx -> {
                        double score = calculateRelevanceScore(idx, keywords, queryLower);
                        return new ScoredIndex(idx, score);
                    })
                    .filter(si -> si.score > 0)
                    .sorted(Comparator.comparingDouble((ScoredIndex si) -> -si.score))
                    .limit(5)
                    .collect(Collectors.toList());

            // 如果没有匹配，回退到最近更新的页面
            if (scored.isEmpty()) {
                scored = allIndex.stream()
                        .sorted(Comparator.comparing(
                                (WikiIndex idx) -> idx.getUpdatedAt() != null ? idx.getUpdatedAt() : java.time.Instant.EPOCH)
                                .reversed())
                        .limit(3)
                        .map(idx -> new ScoredIndex(idx, 0))
                        .collect(Collectors.toList());
            }

            return scored.stream()
                    .map(si -> si.index)
                    .collect(Collectors.toList());
        })
        .subscribeOn(Schedulers.boundedElastic())
        // 读取页面内容（响应式，无 block）
        .flatMap(relevant -> {
            if (relevant.isEmpty()) {
                return Mono.just("暂无相关 Wiki 知识库内容。");
            }

            return Flux.fromIterable(relevant)
                    .concatMap(idx -> wikiFileService.readPage(userId, idx.getPagePath())
                            .onErrorResume(e -> {
                                log.warn("读取 wiki 页面失败: {}", idx.getPagePath());
                                return Mono.empty();
                            })
                            .filter(content -> content != null && !content.isEmpty())
                            .map(content -> "## " + idx.getTitle() + "\n" + content))
                    .collectList()
                    .map(parts -> {
                        if (parts.isEmpty()) {
                            return "暂无相关 Wiki 知识库内容。";
                        }
                        StringBuilder context = new StringBuilder();
                        context.append("以下是相关的 Wiki 知识库内容:\n\n");
                        for (String part : parts) {
                            context.append(part).append("\n\n");
                        }
                        return context.toString();
                    });
        });
    }

    /**
     * 计算索引条目与查询的相关性评分
     * 维度：标题精确匹配、标题关键词命中、摘要关键词命中、标签匹配
     */
    private double calculateRelevanceScore(WikiIndex idx, List<String> keywords, String queryLower) {
        double score = 0;
        String title = idx.getTitle() != null ? idx.getTitle().toLowerCase() : "";
        String summary = idx.getSummary() != null ? idx.getSummary().toLowerCase() : "";
        String[] tags = idx.getTags();

        // 1. 标题完全包含查询（高权重）
        if (title.contains(queryLower) || queryLower.contains(title)) {
            score += 10;
        }

        // 2. 标题关键词命中
        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                score += 3;
            }
        }

        // 3. 摘要关键词命中
        for (String keyword : keywords) {
            if (summary.contains(keyword)) {
                score += 1;
            }
        }

        // 4. 标签匹配
        if (tags != null) {
            for (String tag : tags) {
                String tagLower = tag.toLowerCase();
                for (String keyword : keywords) {
                    if (tagLower.contains(keyword)) {
                        score += 2;
                    }
                }
            }
        }

        // 5. 实体类型优先
        if ("entity".equals(idx.getCategory())) {
            score += 0.5;
        }

        return score;
    }

    /**
     * 评分结果内部类
     */
    private static class ScoredIndex {
        final WikiIndex index;
        final double score;

        ScoredIndex(WikiIndex index, double score) {
            this.index = index;
            this.score = score;
        }
    }
}
