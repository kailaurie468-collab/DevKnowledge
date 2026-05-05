package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.KnowledgeLink;
import com.devknowledge.model.StringArrayTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 知识链接 Mapper
 */
@Mapper
public interface KnowledgeLinkMapper extends BaseMapper<KnowledgeLink> {

    /**
     * 全文搜索知识链接
     * 使用 PostgreSQL 的 tsvector + GIN 索引，按相关度排序
     *
     * @param query 搜索关键词
     * @param limit 返回条数
     * @return 匹配的知识链接列表（包含 relevance_score）
     */
    @Select("""
        SELECT id::text, framework_id::text, title, url, anchor, description, tags, popularity_score,
               ts_rank(search_vector, plainto_tsquery('english', #{query})) AS relevance_score
        FROM knowledge_links
        WHERE search_vector @@ plainto_tsquery('english', #{query})
        ORDER BY relevance_score DESC, popularity_score DESC
        LIMIT #{limit}
        """)
    @Results(id = "searchResult", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "frameworkId", column = "framework_id"),
            @Result(property = "title", column = "title"),
            @Result(property = "url", column = "url"),
            @Result(property = "anchor", column = "anchor"),
            @Result(property = "description", column = "description"),
            @Result(property = "tags", column = "tags", typeHandler = StringArrayTypeHandler.class),
            @Result(property = "popularityScore", column = "popularity_score"),
            @Result(property = "relevanceScore", column = "relevance_score")
    })
    List<KnowledgeLinkSearchResult> fullTextSearch(@Param("query") String query, @Param("limit") int limit);

    /**
     * 全文搜索结果（具体类，便于 MyBatis 映射）
     */
    class KnowledgeLinkSearchResult {
        private String id;
        private String frameworkId;
        private String title;
        private String url;
        private String anchor;
        private String description;
        private String[] tags;
        private Integer popularityScore;
        private Double relevanceScore;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getFrameworkId() { return frameworkId; }
        public void setFrameworkId(String frameworkId) { this.frameworkId = frameworkId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getAnchor() { return anchor; }
        public void setAnchor(String anchor) { this.anchor = anchor; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String[] getTags() { return tags; }
        public void setTags(String[] tags) { this.tags = tags; }
        public Integer getPopularityScore() { return popularityScore; }
        public void setPopularityScore(Integer popularityScore) { this.popularityScore = popularityScore; }
        public Double getRelevanceScore() { return relevanceScore; }
        public void setRelevanceScore(Double relevanceScore) { this.relevanceScore = relevanceScore; }
    }
}
