package com.devknowledge.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * RRF（Reciprocal Rank Fusion）融合排序工具类
 * 将多路召回结果按排名融合，公式：RRF_score(d) = Σ 1/(k + rank_i(d))
 * 其中 k 为平滑常数（默认 60），rank 从 0 开始
 */
@Component
public class RrfRanker {

    /** RRF 平滑常数，避免排名靠前的文档得分过高 */
    private static final int DEFAULT_K = 60;

    /**
     * RRF 融合两路排序结果
     *
     * @param list1        第一路召回结果（如 BM25）
     * @param list2        第二路召回结果（如向量检索）
     * @param k            RRF 平滑常数
     * @param topN         返回结果数上限
     * @param idExtractor  提取元素唯一标识的函数（如 chunk ID）
     * @return 按 RRF 得分降序排列的融合结果
     */
    public <T> List<T> merge(List<T> list1, List<T> list2, int k, int topN,
                             Function<T, UUID> idExtractor) {
        // 两路都为空则直接返回
        if ((list1 == null || list1.isEmpty()) && (list2 == null || list2.isEmpty())) {
            return List.of();
        }
        // 单路为空则返回另一路（截取 topN）
        if (list1 == null || list1.isEmpty()) {
            return list2.subList(0, Math.min(topN, list2.size()));
        }
        if (list2 == null || list2.isEmpty()) {
            return list1.subList(0, Math.min(topN, list1.size()));
        }

        // 收集所有唯一 ID 及其对应的元素和 RRF 得分
        Map<UUID, Double> rrfScores = new LinkedHashMap<>();
        Map<UUID, T> idToItem = new LinkedHashMap<>();

        // 第一路：按排名累加 RRF 分数，rank 从 0 开始
        for (int rank = 0; rank < list1.size(); rank++) {
            T item = list1.get(rank);
            UUID id = idExtractor.apply(item);
            rrfScores.merge(id, 1.0 / (k + rank), Double::sum);
            idToItem.putIfAbsent(id, item);
        }

        // 第二路：按排名累加 RRF 分数
        for (int rank = 0; rank < list2.size(); rank++) {
            T item = list2.get(rank);
            UUID id = idExtractor.apply(item);
            rrfScores.merge(id, 1.0 / (k + rank), Double::sum);
            idToItem.putIfAbsent(id, item);
        }

        // 按 RRF 得分降序排列，取 topN
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> idToItem.get(entry.getKey()))
                .toList();
    }

    /**
     * 使用默认 k=60 的 RRF 融合
     */
    public <T> List<T> merge(List<T> list1, List<T> list2, int topN,
                             Function<T, UUID> idExtractor) {
        return merge(list1, list2, DEFAULT_K, topN, idExtractor);
    }
}
