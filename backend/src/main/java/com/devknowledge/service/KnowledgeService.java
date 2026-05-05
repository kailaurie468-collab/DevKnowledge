package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.LinkSearchResult;
import com.devknowledge.mapper.FrameworkMapper;
import com.devknowledge.mapper.KnowledgeLinkMapper;
import com.devknowledge.model.Framework;
import com.devknowledge.model.KnowledgeLink;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识搜索服务
 * 提供框架列表、框架下的链接列表、全文搜索
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final FrameworkMapper frameworkMapper;
    private final KnowledgeLinkMapper knowledgeLinkMapper;

    /**
     * 获取所有框架列表
     * 按分类分组，返回前端 FrameworkGrid 组件所需数据
     *
     * @return 框架列表
     */
    public Mono<List<Framework>> getFrameworks() {
        return Mono.fromCallable(() ->
                frameworkMapper.selectList(new LambdaQueryWrapper<Framework>().orderByAsc(Framework::getCategory))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取指定框架下的所有知识链接
     * 按热度分降序排列
     *
     * @param slug 框架的 URL 标识，如 "react"
     * @return 知识链接列表；框架不存在时返回空列表
     */
    public Mono<List<KnowledgeLink>> getFrameworkLinks(String slug) {
        return Mono.fromCallable(() -> {
            Framework fw = frameworkMapper.selectOne(
                    new LambdaQueryWrapper<Framework>().eq(Framework::getSlug, slug));
            if (fw == null) {
                return Collections.<KnowledgeLink>emptyList();
            }
            return knowledgeLinkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeLink>()
                            .eq(KnowledgeLink::getFrameworkId, fw.getId())
                            .orderByDesc(KnowledgeLink::getPopularityScore));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 全文搜索知识链接
     * 使用 PostgreSQL tsvector + GIN 索引，按相关度排序
     * 返回结果包含关联的框架名称和相关度分数
     *
     * @param query 搜索关键词，如 "useEffect"
     * @return 搜索结果列表（最多 20 条）
     */
    public Mono<List<LinkSearchResult>> searchLinks(String query) {
        return Mono.fromCallable(() -> {
            // 1. 全文搜索
            List<KnowledgeLinkMapper.KnowledgeLinkSearchResult> results =
                    knowledgeLinkMapper.fullTextSearch(query, 20);

            if (results.isEmpty()) {
                return Collections.<LinkSearchResult>emptyList();
            }

            // 2. 收集所有 framework_id，批量查询框架名称
            List<UUID> fwUuids = results.stream()
                    .map(r2 -> UUID.fromString(r2.getFrameworkId()))
                    .distinct()
                    .collect(Collectors.toList());

            Map<String, String> fwNameMap = new HashMap<>();
            if (!fwUuids.isEmpty()) {
                List<Framework> frameworks = frameworkMapper.selectList(
                        new LambdaQueryWrapper<Framework>().in(Framework::getId, fwUuids));
                for (Framework fw : frameworks) {
                    fwNameMap.put(fw.getId().toString(), fw.getName());
                }
            }

            // 3. 组装返回结果
            return results.stream().map(r -> {
                LinkSearchResult result = new LinkSearchResult();

                LinkSearchResult.LinkInfo link = new LinkSearchResult.LinkInfo();
                link.setId(UUID.fromString(r.getId()));
                link.setFrameworkId(UUID.fromString(r.getFrameworkId()));
                link.setTitle(r.getTitle());
                link.setUrl(r.getUrl());
                link.setAnchor(r.getAnchor());
                link.setDescription(r.getDescription());
                link.setTags(r.getTags());
                link.setPopularityScore(r.getPopularityScore());

                result.setLink(link);
                result.setFrameworkName(fwNameMap.getOrDefault(r.getFrameworkId(), "Unknown"));
                result.setRelevanceScore(r.getRelevanceScore());

                return result;
            }).collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
