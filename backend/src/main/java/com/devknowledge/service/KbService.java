package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.KbCreateRequest;
import com.devknowledge.mapper.KbDocumentMapper;
import com.devknowledge.mapper.KnowledgeBaseMapper;
import com.devknowledge.model.KbDocument;
import com.devknowledge.model.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.devknowledge.dto.KbChunkSearchResult;
import com.devknowledge.mapper.KbChunkMapper;
import com.devknowledge.model.KbChunk;
import com.devknowledge.model.UserEmbeddingConfig;
import com.devknowledge.model.UserRerankerConfig;
import com.devknowledge.security.AesUtil;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class KbService {

    private static final Logger log = LoggerFactory.getLogger(KbService.class);
    private static final int MAX_DOCUMENTS_PER_KB = 200;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    // 默认切分参数
    private static final int DEFAULT_MIN_CHUNK_SIZE = 100;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 1000;

    private final KnowledgeBaseMapper kbMapper;
    private final KbDocumentMapper docMapper;
    private final FileParserService fileParserService;
    private final EmbeddingService embeddingService;
    private final EmbeddingConfigService embeddingConfigService;
    private final EmbeddingUsageService embeddingUsageService;
    private final KbChunkMapper chunkMapper;
    private final MarkdownChunker markdownChunker;
    private final RawFileStorageService rawFileStorageService;
    private final JiebaSegmenter jiebaSegmenter;
    private final RrfRanker rrfRanker;
    private final RerankerService rerankerService;
    private final RerankerConfigService rerankerConfigService;

    @Value("${jwt.secret}")
    private String aesSecret;

    private final ExecutorService parseExecutor = Executors.newFixedThreadPool(3);

    // ==================== 知识库 CRUD ====================

    public Mono<KnowledgeBase> createKb(UUID userId, KbCreateRequest req) {
        return Mono.fromCallable(() -> {
            // 计算新排序值：当前用户最大 sortOrder + 1
            KnowledgeBase lastKb = kbMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeBase>()
                            .eq(KnowledgeBase::getUserId, userId)
                            .orderByDesc(KnowledgeBase::getSortOrder)
                            .last("LIMIT 1"));
            int nextOrder = (lastKb != null && lastKb.getSortOrder() != null) ? lastKb.getSortOrder() + 1 : 1;

            KnowledgeBase kb = new KnowledgeBase();
            kb.setId(UUID.randomUUID());
            kb.setUserId(userId);
            kb.setName(req.getName());
            kb.setDescription(req.getDescription());
            kb.setCreatedAt(Instant.now());
            kb.setUpdatedAt(Instant.now());
            kb.setEmbeddingModel(req.getEmbeddingModel() != null
                    ? req.getEmbeddingModel() : "text-embedding-3-small");
            kb.setSortOrder(nextOrder);
            kbMapper.insert(kb);
            return kb;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<KnowledgeBase>> getUserKbs(UUID userId) {
        return Mono.fromCallable(() ->
                kbMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBase>()
                                .eq(KnowledgeBase::getUserId, userId)
                                .orderByAsc(KnowledgeBase::getSortOrder))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<KnowledgeBase> getKb(UUID id, UUID userId) {
        return Mono.fromCallable(() -> {
            KnowledgeBase kb = kbMapper.selectById(id);
            if (kb != null && !kb.getUserId().equals(userId)) return null;
            return kb;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteKb(UUID id, UUID userId) {
        return Mono.fromCallable(() -> {
            KnowledgeBase kb = kbMapper.selectById(id);
            if (kb == null) throw new RuntimeException("知识库不存在");
            if (!kb.getUserId().equals(userId)) throw new RuntimeException("无权删除");
            kbMapper.deleteById(id);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 批量更新知识库排序
     * 按传入的 ID 顺序依次设置 sort_order
     */
    public Mono<Void> reorderKbs(UUID userId, List<UUID> orderedIds) {
        return Mono.fromCallable(() -> {
            for (int i = 0; i < orderedIds.size(); i++) {
                KnowledgeBase kb = kbMapper.selectById(orderedIds.get(i));
                if (kb == null || !kb.getUserId().equals(userId)) continue;
                kb.setSortOrder(i + 1);
                kbMapper.updateById(kb);
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ==================== 文档管理 ====================

    /**
     * 上传文档
     * 流程：1) 存储原始文件 2) 解析文本 3) 异步切分+向量化
     */
    public Mono<KbDocument> uploadDocument(UUID kbId, String filename, long fileSize, byte[] content, Integer minChunkSize, Integer maxChunkSize) {
        return Mono.fromCallable(() -> {
            if (fileSize > MAX_FILE_SIZE) {
                throw new RuntimeException("文件大小超过 10MB 限制");
            }
            long docCount = docMapper.selectCount(
                    new LambdaQueryWrapper<KbDocument>().eq(KbDocument::getKbId, kbId));
            if (docCount >= MAX_DOCUMENTS_PER_KB) {
                throw new RuntimeException("知识库文档数量已达上限（" + MAX_DOCUMENTS_PER_KB + "）");
            }

            String ext = getExtension(filename).toLowerCase();
            if (!List.of("txt", "md", "markdown", "pdf", "docx").contains(ext)) {
                throw new RuntimeException("不支持的文件格式: " + ext);
            }

            KbDocument doc = new KbDocument();
            doc.setId(UUID.randomUUID());
            doc.setKbId(kbId);
            doc.setFilename(filename);
            doc.setFileType(ext);
            doc.setFileSize(fileSize);
            doc.setStatus("processing");
            doc.setCreatedAt(Instant.now());
            docMapper.insert(doc);

            // 存储原始文件到磁盘（用于后续重试）
            try {
                rawFileStorageService.store(kbId, doc.getId(), filename, content);
            } catch (Exception e) {
                log.warn("存储原始文件失败（不影响解析）: {}", e.getMessage());
            }

            parseExecutor.submit(() -> {
                try {
                    // 根据文件大小选择解析策略：< 1MB 直接解析，>= 1MB 流式解析
                    String text = fileParserService.parse(filename, content);
                    doc.setContent(text);
                    doc.setStatus("embedding");
                    log.info("文档解析完成: {} ({}字)，开始向量化", filename, text.length());
                } catch (Exception e) {
                    doc.setStatus("error");
                    doc.setErrorMessage(e.getMessage());
                    log.error("文档解析失败: {} - {}", filename, e.getMessage());
                }
                docMapper.updateById(doc);

                // 触发切分 + 向量化
                if ("embedding".equals(doc.getStatus())) {
                    try {
                        chunkAndEmbed(kbId, doc.getId(), doc.getContent(), minChunkSize, maxChunkSize);
                        doc.setStatus("ready");
                        log.info("文档向量化完成: {}", filename);
                        // 解析+向量化全部成功，删除原始文件释放磁盘空间
                        rawFileStorageService.delete(kbId, doc.getId());
                    } catch (Exception embedEx) {
                        doc.setStatus("error");
                        doc.setErrorMessage("向量化失败: " + embedEx.getMessage());
                        log.warn("文档向量化失败: {} - {}", filename, embedEx.getMessage());
                    }
                    docMapper.updateById(doc);
                }
            });

            return doc;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 文档解析重试
     * 从磁盘读取原始文件，重新执行解析 + 向量化
     */
    public Mono<KbDocument> retryDocument(UUID docId, UUID userId) {
        return Mono.fromCallable(() -> {
            KbDocument doc = docMapper.selectById(docId);
            if (doc == null) throw new RuntimeException("文档不存在");

            KnowledgeBase kb = kbMapper.selectById(doc.getKbId());
            if (kb == null || !kb.getUserId().equals(userId)) {
                throw new RuntimeException("无权操作");
            }

            if (!"error".equals(doc.getStatus())) {
                throw new RuntimeException("文档状态不是错误，无需重试");
            }

            // 读取原始文件
            byte[] rawContent;
            try {
                rawContent = rawFileStorageService.read(kb.getId(), docId, doc.getFilename());
            } catch (Exception e) {
                throw new RuntimeException("原始文件不存在，无法重试: " + e.getMessage());
            }

            // 重置状态
            doc.setStatus("processing");
            doc.setErrorMessage(null);
            doc.setContent(null);
            doc.setChunkCount(null);
            docMapper.updateById(doc);

            // 异步重新解析
            byte[] finalRawContent = rawContent;
            parseExecutor.submit(() -> {
                try {
                    String text = fileParserService.parse(doc.getFilename(), finalRawContent);
                    doc.setContent(text);
                    doc.setStatus("embedding");
                    log.info("重试解析完成: {} ({}字)", doc.getFilename(), text.length());
                } catch (Exception e) {
                    doc.setStatus("error");
                    doc.setErrorMessage(e.getMessage());
                    log.error("重试解析失败: {} - {}", doc.getFilename(), e.getMessage());
                }
                docMapper.updateById(doc);

                if ("embedding".equals(doc.getStatus())) {
                    try {
                        chunkAndEmbed(doc.getKbId(), doc.getId(), doc.getContent(), null, null);
                        doc.setStatus("ready");
                        log.info("重试向量化完成: {}", doc.getFilename());
                        // 重试成功，删除原始文件
                        rawFileStorageService.delete(doc.getKbId(), doc.getId());
                    } catch (Exception embedEx) {
                        doc.setStatus("error");
                        doc.setErrorMessage("向量化失败: " + embedEx.getMessage());
                    }
                    docMapper.updateById(doc);
                }
            });

            return doc;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<KbDocument>> getDocuments(UUID kbId) {
        return Mono.fromCallable(() ->
                docMapper.selectList(
                        new LambdaQueryWrapper<KbDocument>()
                                .eq(KbDocument::getKbId, kbId)
                                .orderByDesc(KbDocument::getCreatedAt))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteDocument(UUID docId, UUID userId) {
        return Mono.fromCallable(() -> {
            KbDocument doc = docMapper.selectById(docId);
            if (doc == null) throw new RuntimeException("文档不存在");
            KnowledgeBase kb = kbMapper.selectById(doc.getKbId());
            if (kb == null || !kb.getUserId().equals(userId)) throw new RuntimeException("无权删除");

            // 删除关联的 chunks
            chunkMapper.delete(
                    new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, docId));

            // 删除原始文件
            rawFileStorageService.delete(kb.getId(), docId);

            docMapper.deleteById(docId);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ==================== 搜索 ====================

    public Mono<List<KbDocument>> searchKb(UUID kbId, String query) {
        return Mono.fromCallable(() ->
                docMapper.selectList(
                        new LambdaQueryWrapper<KbDocument>()
                                .eq(KbDocument::getKbId, kbId)
                                .eq(KbDocument::getStatus, "ready")
                                .like(KbDocument::getContent, query)
                                .last("LIMIT 20"))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    // ==================== Embedding 配置检查 ====================

    /**
     * 检查用户是否已配置 Embedding AI
     * 用于 Demo 生成时判断 RAG 检索模式
     */
    public boolean hasEmbeddingConfig(UUID userId) {
        return embeddingConfigService.getActiveConfig(userId) != null;
    }

    // ==================== 向量化 ====================

    /**
     * 文档切分 + 向量化
     * Markdown 内容使用 flexmark-java AST 切分，纯文本使用段落切分
     */
    private void chunkAndEmbed(UUID kbId, UUID docId, String content, Integer minChunkSize, Integer maxChunkSize) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) return;

        String model = kb.getEmbeddingModel();

        UserEmbeddingConfig embedConfig = embeddingConfigService.getActiveConfig(kb.getUserId());
        if (embedConfig == null) {
            log.warn("用户未配置 Embedding AI，跳过向量化");
            // 标记文档为未向量化，设置警告信息
            KbDocument doc = docMapper.selectById(docId);
            if (doc != null) {
                doc.setChunkCount(0);
                doc.setWarningMessage("未配置 Embedding AI，文档未向量化。配置后可获得更精准的语义检索效果。");
                docMapper.updateById(doc);
            }
            return;
        }
        AesUtil aes = new AesUtil(aesSecret);
        String apiKey = aes.decrypt(embedConfig.getApiKey());

        // 使用 MarkdownChunker 或纯文本切分
        List<String> chunks = splitIntoChunks(content, minChunkSize, maxChunkSize);
        log.info("文档切分完成: {} 个 chunk", chunks.size());

        int totalTokens = 0;
        int chunkIndex = 0;
        List<KbChunk> chunksList = new ArrayList<>();
        for (List<String> batch : partition(chunks, 5)) {
            EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                    batch, embedConfig.getBaseUrl(), apiKey, model, EmbeddingService.VECTOR_DIMENSION);
            totalTokens += result.promptTokens();

            // 批量插入 chunk（含 Jieba 分词 tsvector）
            for (int i = 0; i < batch.size(); i++) {
                KbChunk chunk = new KbChunk();
                chunk.setId(UUID.randomUUID());
                chunk.setKbId(kbId);
                chunk.setDocId(docId);
                chunk.setChunkIndex(chunkIndex++);
                chunk.setContent(batch.get(i));
                chunk.setEmbedding(EmbeddingService.vectorToString(result.vectors().get(i)));
                chunk.setCreatedAt(Instant.now());
                // Jieba 分词后存入 tsvector 列，用于 BM25 关键词检索
                chunk.setTsv(jiebaSegmenter.segment(batch.get(i)));
                chunksList.add(chunk);
            }
            chunkMapper.insertBatchWithTsv(chunksList);
            chunksList.clear();
            // 批间延迟，避免 API 限流
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        embeddingUsageService.recordUsage(kb.getUserId(), embedConfig.getId(), totalTokens);

        // 更新文档的 chunk_count
        KbDocument doc = docMapper.selectById(docId);
        if (doc != null) {
            doc.setChunkCount(chunks.size());
            docMapper.updateById(doc);
        }
    }

    // ==================== 文档切分 ====================

    /**
     * 切分内容为 chunk 列表
     * Markdown 内容使用 flexmark-java AST 切分，纯文本使用段落切分
     */
    private List<String> splitIntoChunks(String content, Integer minChunkSize, Integer maxChunkSize) {
        if (content == null || content.isBlank()) return List.of();

        if (isMarkdownContent(content)) {
            // 使用 flexmark-java AST 切分
            return markdownChunker.split(content, minChunkSize, maxChunkSize);
        } else {
            // 纯文本：使用段落切分
            int minSize = (minChunkSize != null && minChunkSize >= 20) ? minChunkSize : DEFAULT_MIN_CHUNK_SIZE;
            int maxSize = (maxChunkSize != null && maxChunkSize >= minSize + 50) ? maxChunkSize : DEFAULT_MAX_CHUNK_SIZE;
            return splitPlainText(content, minSize, maxSize);
        }
    }

    /**
     * 检测内容是否为 Markdown 格式
     * 通过检查代码围栏、标题、表格、列表等特征判断
     */
    private boolean isMarkdownContent(String content) {
        if (content == null) return false;
        String[] lines = content.split("\n");
        int mdFeatureCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) mdFeatureCount++;
            else if (trimmed.matches("^#{1,6}\\s+.+")) mdFeatureCount++;
            else if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 2) mdFeatureCount++;
            else if (trimmed.matches("^[-*+]\\s+.*") || trimmed.matches("^\\d+\\.\\s+.*")) mdFeatureCount++;
        }
        // 至少出现 3 处 Markdown 特征才判定为 Markdown
        return mdFeatureCount >= 3;
    }

    /**
     * 纯文本切分
     */
    private List<String> splitPlainText(String content, int minSize, int maxSize) {
        String[] rawParts = content.split("\\n\\n+");
        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String part : rawParts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            if (buffer.length() + trimmed.length() < minSize) {
                if (buffer.length() > 0) buffer.append("\n\n");
                buffer.append(trimmed);
            } else {
                if (buffer.length() > 0) {
                    chunks.add(buffer.toString());
                    buffer.setLength(0);
                }
                if (trimmed.length() > maxSize) {
                    for (String sub : splitLongParagraph(trimmed)) {
                        chunks.add(sub);
                    }
                } else {
                    chunks.add(trimmed);
                }
            }
        }
        if (buffer.length() > 0) {
            chunks.add(buffer.toString());
        }
        return chunks;
    }

    private List<String> splitLongParagraph(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + 1000, text.length());
            if (end < text.length()) {
                int breakAt = -1;
                for (int i = end - 1; i > start + 500; i--) {
                    char c = text.charAt(i);
                    if (c == '。' || c == '.' || c == '\n') {
                        breakAt = i + 1;
                        break;
                    }
                }
                if (breakAt > start) end = breakAt;
            }
            result.add(text.substring(start, end));
            start = end;
        }
        return result;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    // ==================== 向量检索 ====================

    /**
     * 混合检索知识库（BM25 + 向量 + RRF 融合排序）
     * BM25 和 Embedding 两条通道各召回 top-20，使用 RRF 融合重排后输出 topK 候选集
     */
    public Mono<List<KbChunkSearchResult>> searchKbVector(UUID userId, UUID kbId, String query, int topK) {
        return Mono.fromCallable(() -> {
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb == null) return List.<KbChunkSearchResult>of();

            // 通道一：BM25 关键词检索（top-20）
            String tsQuery = jiebaSegmenter.buildTsQuery(query);
            List<KbChunkSearchResult> bm25Results = tsQuery.isBlank()
                    ? List.of()
                    : chunkMapper.searchByBm25(kbId, tsQuery, 20);
            log.info("BM25 召回 {} 条, tsQuery={}", bm25Results.size(), tsQuery);

            // 通道二：向量检索（top-20）
            List<KbChunkSearchResult> vectorResults;
            UserEmbeddingConfig embedConfig = embeddingConfigService.getActiveConfig(userId);
            if (embedConfig == null) {
                log.warn("用户未配置 Embedding AI，向量通道回退到 LIKE 搜索");
                vectorResults = searchKbFallback(kbId, query);
            } else {
                AesUtil aes = new AesUtil(aesSecret);
                String apiKey = aes.decrypt(embedConfig.getApiKey());
                EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                        List.of(query), embedConfig.getBaseUrl(), apiKey,
                        kb.getEmbeddingModel(), 1024);
                float[] queryVector = result.vectors().get(0);
                embeddingUsageService.recordUsage(userId, embedConfig.getId(), result.promptTokens());
                String vectorStr = EmbeddingService.vectorToString(queryVector);
                vectorResults = chunkMapper.searchByVector(kbId, vectorStr, 20);
            }
            log.info("向量通道召回 {} 条", vectorResults.size());

            // RRF 融合排序：候选池取较大值，给 Reranker 足够的精选空间
            UserRerankerConfig rerankerConfig;
            boolean hasReranker = (rerankerConfig = rerankerConfigService.getActiveConfig(userId)) != null;
            int candidateSize = hasReranker ? Math.max(topK * 5, 20) : topK;
            List<KbChunkSearchResult> merged = rrfRanker.merge(
                    bm25Results, vectorResults, 60, candidateSize, KbChunkSearchResult::getId);
            log.info("RRF 融合后返回 {} 条（候选池={}）", merged.size(), candidateSize);

            // 精排：Reranker 交叉编码重排序（仅在用户配置了 Reranker 时执行）
            if (rerankerConfig != null && !merged.isEmpty()) {
                try {
                    AesUtil aes2 = new AesUtil(aesSecret);
                    String rerankerApiKey = aes2.decrypt(rerankerConfig.getApiKey());
                    // 将候选集文本传给 Reranker，index 对应 merged 列表的下标
                    List<String> texts = merged.stream().map(KbChunkSearchResult::getContent).toList();
                    List<RerankerService.RerankResult> reranked = rerankerService.rerank(
                            query, texts, rerankerConfig.getBaseUrl(), rerankerApiKey,
                            rerankerConfig.getModel(), topK);
                    // 按 Reranker 返回的 index 映射回原始候选集，取 topK
                    final List<KbChunkSearchResult> finalMerged = merged;
                    merged = reranked.stream()
                            .filter(r -> r.index() < finalMerged.size())
                            .limit(topK)
                            .map(r -> finalMerged.get(r.index()))
                            .toList();
                    log.info("Reranker 精排完成: {} 条（从 {} 条候选中精选）", merged.size(), finalMerged.size());
                } catch (Exception e) {
                    // 精排失败不阻断检索，降级使用 RRF 结果
                    log.warn("Reranker 精排失败，降级使用 RRF 结果: {}", e.getMessage());
                    // 降级时截取 topK
                    if (merged.size() > topK) {
                        merged = merged.subList(0, topK);
                    }
                }
            } else {
                // 无 Reranker 时直接截取 topK
                if (merged.size() > topK) {
                    merged = merged.subList(0, topK);
                }
            }

            return merged;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * BM25 关键词检索
     * 对 query 进行 Jieba 分词，拼接 tsquery 执行 PostgreSQL ts_rank 排序
     */
    public Mono<List<KbChunkSearchResult>> searchByBm25(UUID kbId, String query, int topK) {
        return Mono.fromCallable(() -> {
            String tsQuery = jiebaSegmenter.buildTsQuery(query);
            if (tsQuery.isBlank()) return List.<KbChunkSearchResult>of();
            return chunkMapper.searchByBm25(kbId, tsQuery, topK);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * LIKE 回退搜索（Embedding 未配置时使用）
     */
    private List<KbChunkSearchResult> searchKbFallback(UUID kbId, String query) {
        List<KbDocument> docs = docMapper.selectList(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getKbId, kbId)
                        .eq(KbDocument::getStatus, "ready")
                        .like(KbDocument::getContent, query)
                        .last("LIMIT 5"));
        List<KbChunkSearchResult> results = new ArrayList<>();
        for (KbDocument doc : docs) {
            KbChunkSearchResult r = new KbChunkSearchResult();
            r.setDocId(doc.getId());
            r.setFilename(doc.getFilename());
            r.setContent(doc.getContent() != null ? doc.getContent().substring(0, Math.min(doc.getContent().length(), 500)) : "");
            r.setScore(0.5);
            results.add(r);
        }
        return results;
    }
}
