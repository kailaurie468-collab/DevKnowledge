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

    @Value("${jwt.secret}")
    private String aesSecret;

    private final ExecutorService parseExecutor = Executors.newFixedThreadPool(3);

    // ==================== 知识库 CRUD ====================

    public Mono<KnowledgeBase> createKb(UUID userId, KbCreateRequest req) {
        return Mono.fromCallable(() -> {
            KnowledgeBase kb = new KnowledgeBase();
            kb.setId(UUID.randomUUID());
            kb.setUserId(userId);
            kb.setName(req.getName());
            kb.setDescription(req.getDescription());
            kb.setCreatedAt(Instant.now());
            kb.setUpdatedAt(Instant.now());
            kb.setEmbeddingModel(req.getEmbeddingModel() != null
                    ? req.getEmbeddingModel() : "text-embedding-3-small");
            kb.setEmbeddingDimensions(req.getEmbeddingDimensions());
            kbMapper.insert(kb);
            return kb;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<KnowledgeBase>> getUserKbs(UUID userId) {
        return Mono.fromCallable(() ->
                kbMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBase>()
                                .eq(KnowledgeBase::getUserId, userId)
                                .orderByDesc(KnowledgeBase::getCreatedAt))
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

    // ==================== 文档管理 ====================

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

            parseExecutor.submit(() -> {
                try {
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

    // ==================== 向量化 ====================

    /**
     * 文档切分 + 向量化
     */
    private void chunkAndEmbed(UUID kbId, UUID docId, String content, Integer minChunkSize, Integer maxChunkSize) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) return;

        String model = kb.getEmbeddingModel();
        Integer dimensions = kb.getEmbeddingDimensions();

        UserEmbeddingConfig embedConfig = embeddingConfigService.getActiveConfig(kb.getUserId());
        if (embedConfig == null) {
            log.warn("用户未配置 Embedding AI，跳过向量化");
            return;
        }
        AesUtil aes = new AesUtil(aesSecret);
        String apiKey = aes.decrypt(embedConfig.getApiKey());
        // 切分文档
        List<String> chunks = splitIntoChunks(content, minChunkSize, maxChunkSize);
        log.info("文档切分完成: {} 个 chunk", chunks.size());

        int totalTokens = 0;
        int chunkIndex = 0;
        List<KbChunk> chunksList = new ArrayList<>();
        for (List<String> batch : partition(chunks, 20)) {
            EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                    batch, embedConfig.getBaseUrl(), apiKey, model, dimensions);
            totalTokens += result.promptTokens();

            // 批量插入 chunk（content + embedding 一次写入）
            for (int i = 0; i < batch.size(); i++) {
                KbChunk chunk = new KbChunk();
                chunk.setId(UUID.randomUUID());
                chunk.setKbId(kbId);
                chunk.setDocId(docId);
                chunk.setChunkIndex(chunkIndex++);
                chunk.setContent(batch.get(i));
                chunk.setEmbedding(EmbeddingService.vectorToString(result.vectors().get(i)));
                chunk.setCreatedAt(Instant.now());
                chunksList.add(chunk);
            }
            chunkMapper.insert(chunksList);
            chunksList.clear();
            if (chunks.size() > 20) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }

        embeddingUsageService.recordUsage(kb.getUserId(), embedConfig.getId(), totalTokens);

        // 更新文档的 chunk_count
        KbDocument doc = docMapper.selectById(docId);
        if (doc != null) {
            doc.setChunkCount(chunks.size());
            docMapper.updateById(doc);
        }
    }

    // ==================== 段落切分 ====================

    // Markdown 块类型枚举
    private enum BlockType { CODE, HEADING, LIST, TABLE, PARAGRAPH }

    // Markdown 结构块
    private static class MarkdownBlock {
        final BlockType type;
        final String content;
        final String headingPrefix; // 用于 HEADING 类型，记录标题前缀
        MarkdownBlock(BlockType type, String content, String headingPrefix) {
            this.type = type;
            this.content = content;
            this.headingPrefix = headingPrefix;
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
     * 状态机解析 Markdown 内容为结构块列表
     * 同类型的连续行会合并为一个块
     */
    private List<MarkdownBlock> extractMarkdownBlocks(String content) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        BlockType currentType = null;
        // 结果缓冲区
        StringBuilder buffer = new StringBuilder();
        String currentHeadingPrefix = null;

        for (String line : lines) {
            String trimmed = line.trim();
            BlockType lineType = detectLineType(trimmed, currentType);

            if (currentType != null && lineType != currentType) {
                // 类型变化，刷出缓冲区
                flushBuffer(blocks, currentType, buffer, currentHeadingPrefix);
                buffer.setLength(0);
                currentHeadingPrefix = null;
            }

            currentType = lineType;
            if (lineType == BlockType.HEADING && currentHeadingPrefix == null) {
                // 提取标题前缀（如 "## "）
                int spaceIdx = trimmed.indexOf(' ');
                currentHeadingPrefix = spaceIdx > 0 ? trimmed.substring(0, spaceIdx + 1) : trimmed + " ";
            }
            if (buffer.length() > 0) buffer.append("\n");
            buffer.append(line);
        }

        // 刷出最后一个缓冲区
        if (buffer.length() > 0) {
            flushBuffer(blocks, currentType, buffer, currentHeadingPrefix);
        }
        return blocks;
    }

    /**
     * 检测单行的 Markdown 类型
     * 在代码围栏内部时保持 CODE 类型
     */
    private BlockType detectLineType(String trimmed, BlockType currentType) {
        if (trimmed.startsWith("```")) return BlockType.CODE;
        if (currentType == BlockType.CODE) return BlockType.CODE;
        if (trimmed.matches("^#{1,6}\\s+.+")) return BlockType.HEADING;
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 2) return BlockType.TABLE;
        if (trimmed.matches("^[-*+]\\s+.*") || trimmed.matches("^\\d+\\.\\s+.*")) return BlockType.LIST;
        return BlockType.PARAGRAPH;
    }

    /**
     * 将缓冲区内容刷出为 MarkdownBlock
     */
    private void flushBuffer(List<MarkdownBlock> blocks, BlockType type, StringBuilder buffer, String headingPrefix) {
        if (buffer.length() == 0) return;
        String text = buffer.toString().trim();
        if (text.isEmpty()) return;
        blocks.add(new MarkdownBlock(type, text, headingPrefix));
    }

    /**
     * 根据 minSize/maxSize 调整块大小
     * CODE 块原样输出；HEADING 块超长时按标题拆分；其他块合并或拆分
     */
    private List<String> adjustBlockSize(List<MarkdownBlock> blocks, int minSize, int maxSize) {
        List<String> result = new ArrayList<>();
        StringBuilder mergeBuffer = new StringBuilder();

        for (MarkdownBlock block : blocks) {
            if (block.type == BlockType.CODE) {
                // 代码块原样输出，先刷出合并缓冲区
                if (mergeBuffer.length() > 0) {
                    result.add(mergeBuffer.toString());
                    mergeBuffer.setLength(0);
                }
                result.add(block.content);
                continue;
            }

            if (block.type == BlockType.HEADING && block.content.length() > maxSize) {
                // 标题块过长：刷出合并缓冲区，按子标题拆分
                if (mergeBuffer.length() > 0) {
                    result.add(mergeBuffer.toString());
                    mergeBuffer.setLength(0);
                }
                // 从内容中分离标题行和正文
                int firstNewline = block.content.indexOf('\n');
                String headingLine = firstNewline > 0 ? block.content.substring(0, firstNewline) : block.content;
                String body = firstNewline > 0 ? block.content.substring(firstNewline + 1) : "";
                // 按空行拆分正文
                String[] bodyParts = body.split("\\n\\n+");
                for (String part : bodyParts) {
                    String trimmed = part.trim();
                    if (trimmed.isEmpty()) continue;
                    String chunk = headingLine + "\n" + trimmed;
                    if (chunk.length() > maxSize) {
                        // 子块仍然过长，用 splitLongParagraph 拆分正文
                        for (String sub : splitLongParagraph(trimmed)) {
                            result.add(headingLine + "\n" + sub);
                        }
                    } else {
                        result.add(chunk);
                    }
                }
                continue;
            }

            // 普通块（PARAGRAPH/LIST/TABLE/短 HEADING）
            if (block.content.length() < minSize) {
                // 过小，尝试合并
                if (mergeBuffer.length() > 0) mergeBuffer.append("\n\n");
                mergeBuffer.append(block.content);
            } else if (block.content.length() > maxSize) {
                // 过大，先刷出合并缓冲区
                if (mergeBuffer.length() > 0) {
                    result.add(mergeBuffer.toString());
                    mergeBuffer.setLength(0);
                }
                if (block.type == BlockType.PARAGRAPH) {
                    // 段落类型用 splitLongParagraph 拆分
                    result.addAll(splitLongParagraph(block.content));
                } else {
                    // 列表/表格：按行拆分后重新组装
                    result.addAll(splitByLines(block.content, maxSize));
                }
            } else {
                // 大小合适，先刷出合并缓冲区，再输出当前块
                if (mergeBuffer.length() > 0) {
                    result.add(mergeBuffer.toString());
                    mergeBuffer.setLength(0);
                }
                result.add(block.content);
            }
        }

        // 刷出最后的合并缓冲区
        if (mergeBuffer.length() > 0) {
            result.add(mergeBuffer.toString());
        }
        return result;
    }

    /**
     * 按行拆分超长内容（用于列表/表格等不适合按段落拆分的类型）
     */
    private List<String> splitByLines(String text, int maxSize) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (buffer.length() + line.length() + 1 > maxSize && buffer.length() > 0) {
                result.add(buffer.toString());
                buffer.setLength(0);
            }
            if (buffer.length() > 0) buffer.append("\n");
            buffer.append(line);
        }
        if (buffer.length() > 0) {
            result.add(buffer.toString());
        }
        return result;
    }

    /**
     * 切分内容为 chunk 列表
     * Markdown 内容使用结构感知切分，纯文本使用原有的空行切分
     */
    private List<String> splitIntoChunks(String content, Integer minChunkSize, Integer maxChunkSize) {
        if (content == null || content.isBlank()) return List.of();

        // 参数校验与默认值
        int minSize = (minChunkSize != null && minChunkSize >= 20) ? minChunkSize : DEFAULT_MIN_CHUNK_SIZE;
        int maxSize = (maxChunkSize != null && maxChunkSize >= minSize + 50) ? maxChunkSize : DEFAULT_MAX_CHUNK_SIZE;

        if (isMarkdownContent(content)) {
            // Markdown 结构感知切分
            List<MarkdownBlock> blocks = extractMarkdownBlocks(content);
            return adjustBlockSize(blocks, minSize, maxSize);
        } else {
            // 纯文本：使用原有逻辑
            return splitPlainText(content, minSize, maxSize);
        }
    }

    /**
     * 纯文本切分（原有逻辑重构，支持自定义大小）
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
     * 向量检索知识库
     */
    public Mono<List<KbChunkSearchResult>> searchKbVector(UUID userId, UUID kbId, String query, int topK) {
        return Mono.fromCallable(() -> {
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb == null) return List.<KbChunkSearchResult>of();

            UserEmbeddingConfig embedConfig = embeddingConfigService.getActiveConfig(userId);
            if (embedConfig == null) {
                log.warn("用户未配置 Embedding AI，回退到 LIKE 搜索");
                return searchKbFallback(kbId, query);
            }

            AesUtil aes = new AesUtil(aesSecret);
            String apiKey = aes.decrypt(embedConfig.getApiKey());
            // 用户提示词的向量
            EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                    List.of(query), embedConfig.getBaseUrl(), apiKey,
                    kb.getEmbeddingModel(), kb.getEmbeddingDimensions());
            float[] queryVector = result.vectors().get(0);

            // 记录 Embedding 使用情况
            embeddingUsageService.recordUsage(userId, embedConfig.getId(), result.promptTokens());

            // 向量转字符串，用于数据库查询
            String vectorStr = EmbeddingService.vectorToString(queryVector);
            // 向量检索
            return chunkMapper.searchByVector(kbId, vectorStr, topK);
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
