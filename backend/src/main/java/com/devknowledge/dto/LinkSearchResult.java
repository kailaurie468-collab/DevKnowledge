package com.devknowledge.dto;

import lombok.Data;

import java.util.UUID;

/**
 * 知识链接搜索结果（包含关联的框架名称和相关度分数）
 */
@Data
public class LinkSearchResult {

    /** 知识链接信息 */
    private LinkInfo link;

    /** 所属框架名称 */
    private String frameworkName;

    /** 搜索相关度分数 */
    private Double relevanceScore;

    /**
     * 链接信息（嵌套对象，匹配前端 LinkSearchResult.link 结构）
     */
    @Data
    public static class LinkInfo {
        private UUID id;
        private UUID frameworkId;
        private String title;
        private String url;
        private String anchor;
        private String description;
        private String[] tags;
        private Integer popularityScore;
    }
}
