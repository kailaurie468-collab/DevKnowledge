package com.devknowledge.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web 搜索服务
 * 通过 Bing 搜索获取实时网页结果，国内可访问
 * 作为本地知识库的补充，当数据库结果不足时触发
 */
@Service
public class WebSearchService {

    private final WebClient webClient;

    public WebSearchService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://cn.bing.com")
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .defaultHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build();
    }

    /**
     * 执行 Web 搜索
     * 请求 Bing 搜索页面，解析搜索结果
     *
     * @param query 搜索关键词
     * @param limit 最大返回条数
     * @return 搜索结果列表
     */
    public Mono<List<WebSearchResult>> search(String query, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search").queryParam("q", query).build())
                .retrieve()
                .bodyToMono(String.class)
                .map(html -> parseResults(html, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 解析 Bing HTML 搜索结果
     * 提取标题、URL、描述
     */
    private List<WebSearchResult> parseResults(String html, int limit) {
        List<WebSearchResult> results = new ArrayList<>();

        // Bing 搜索结果在 <li class="b_algo"> 块中
        // 标题: <h2><a href="url">title</a></h2>
        // 描述: <p class="b_lineclamp...">snippet</p>
        Pattern blockPattern = Pattern.compile(
                "<li class=\"b_algo\"[^>]*>(.*?)</li>", Pattern.DOTALL);
        Pattern linkPattern = Pattern.compile(
                "<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>");
        Pattern snippetPattern = Pattern.compile(
                "<p[^>]*class=\"b_[^\"]*\"[^>]*>(.*?)</p>", Pattern.DOTALL);

        Matcher blockMatcher = blockPattern.matcher(html);

        while (blockMatcher.find() && results.size() < limit) {
            String block = blockMatcher.group(1);

            Matcher linkMatcher = linkPattern.matcher(block);
            if (!linkMatcher.find()) continue;

            String url = linkMatcher.group(1);
            String title = stripHtml(linkMatcher.group(2));

            // 跳过 Bing 内部链接
            if (url.contains("bing.com") || url.contains("microsoft.com")) continue;

            String snippet = "";
            Matcher snippetMatcher = snippetPattern.matcher(block);
            if (snippetMatcher.find()) {
                snippet = stripHtml(snippetMatcher.group(1));
            }

            WebSearchResult result = new WebSearchResult();
            result.setTitle(title);
            result.setUrl(url);
            result.setDescription(snippet);
            results.add(result);
        }

        return results;
    }

    /**
     * 清理 HTML 标签
     */
    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ").trim();
    }

    /**
     * Web 搜索结果
     */
    public static class WebSearchResult {
        private String title;
        private String url;
        private String description;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
