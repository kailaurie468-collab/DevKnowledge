package com.devknowledge.controller;

import com.devknowledge.dto.LinkSearchResult;
import com.devknowledge.model.Framework;
import com.devknowledge.model.KnowledgeLink;
import com.devknowledge.service.KnowledgeService;
import com.devknowledge.service.WebSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 知识搜索接口
 * 提供框架浏览、本地搜索和 Web 搜索功能，无需登录即可访问
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final WebSearchService webSearchService;

    /**
     * 获取所有框架列表
     * 按分类（frontend/backend/mobile）返回
     *
     * @return 框架列表
     */
    @GetMapping("/frameworks")
    public Mono<ResponseEntity<List<Framework>>> getFrameworks() {
        return knowledgeService.getFrameworks()
                .map(ResponseEntity::ok);
    }

    /**
     * 获取指定框架下的知识链接
     * 按热度分降序排列
     *
     * @param slug 框架标识，如 "react"、"spring-boot"
     * @return 知识链接列表
     */
    @GetMapping("/frameworks/{slug}/links")
    public Mono<ResponseEntity<List<KnowledgeLink>>> getFrameworkLinks(@PathVariable String slug) {
        return knowledgeService.getFrameworkLinks(slug)
                .map(ResponseEntity::ok);
    }

    /**
     * 全文搜索本地知识链接
     * 使用 PostgreSQL tsvector 全文索引，返回匹配结果及框架名称
     *
     * @param q 搜索关键词，如 "useEffect"、"dependency injection"
     * @return 搜索结果列表（最多 20 条，按相关度排序）
     */
    @GetMapping("/links/search")
    public Mono<ResponseEntity<List<LinkSearchResult>>> searchLinks(@RequestParam("q") String query) {
        return knowledgeService.searchLinks(query)
                .map(ResponseEntity::ok);
    }

    /**
     * Web 搜索（实时联网搜索）
      * 通过 Bing 搜索获取外部网页结果，作为本地知识库的补充
     *
     * @param q   搜索关键词
     * @param limit 最大返回条数，默认 10
     * @return Web 搜索结果列表
     */
    @GetMapping("/links/web-search")
    public Mono<ResponseEntity<List<WebSearchService.WebSearchResult>>> webSearch(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return webSearchService.search(query, limit)
                .map(ResponseEntity::ok);
    }
}
