package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.mapper.WikiIndexMapper;
import com.devknowledge.model.WikiIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Wiki 检索服务
 * 为 Demo 生成提供基于索引的 Wiki 上下文检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiRetrievalService {

    private final WikiIndexMapper wikiIndexMapper;
    private final WikiFileService wikiFileService;

    /**
     * 根据查询检索相关 wiki 页面上下文
     */
    public Mono<String> retrieveContext(UUID userId, String query) {
        return Mono.fromCallable(() -> {
            // 获取所有索引
            List<WikiIndex> allIndex = wikiIndexMapper.selectList(
                    new LambdaQueryWrapper<WikiIndex>()
                            .eq(WikiIndex::getUserId, userId));

            // 简单关键词匹配
            List<WikiIndex> relevant = allIndex.stream()
                    .filter(idx -> {
                        String searchText = (idx.getTitle() + " " + (idx.getSummary() != null ? idx.getSummary() : "")).toLowerCase();
                        String queryLower = query.toLowerCase();
                        for (String keyword : queryLower.split("\\s+")) {
                            if (keyword.length() > 1 && searchText.contains(keyword)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .limit(5)
                    .collect(Collectors.toList());

            if (relevant.isEmpty()) {
                // 没有匹配时返回前 3 个页面
                relevant = allIndex.stream().limit(3).collect(Collectors.toList());
            }

            // 读取页面内容
            StringBuilder context = new StringBuilder();
            context.append("以下是相关的 Wiki 知识库内容:\n\n");

            for (WikiIndex idx : relevant) {
                try {
                    String content = wikiFileService.readPage(userId, idx.getPagePath()).block();
                    if (content != null && !content.isEmpty()) {
                        context.append("## ").append(idx.getTitle()).append("\n");
                        context.append(content).append("\n\n");
                    }
                } catch (Exception e) {
                    log.warn("读取 wiki 页面失败: {}", idx.getPagePath());
                }
            }

            return context.toString();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
