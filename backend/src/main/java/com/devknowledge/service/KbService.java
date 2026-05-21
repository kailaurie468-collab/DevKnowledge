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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class KbService {

    private static final Logger log = LoggerFactory.getLogger(KbService.class);
    private static final int MAX_DOCUMENTS_PER_KB = 200;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final KnowledgeBaseMapper kbMapper;
    private final KbDocumentMapper docMapper;
    private final FileParserService fileParserService;

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

    public Mono<KbDocument> uploadDocument(UUID kbId, String filename, long fileSize, byte[] content) {
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
                    doc.setStatus("ready");
                    log.info("文档解析完成: {} ({}字)", filename, text.length());
                } catch (Exception e) {
                    doc.setStatus("error");
                    doc.setErrorMessage(e.getMessage());
                    log.error("文档解析失败: {} - {}", filename, e.getMessage());
                }
                docMapper.updateById(doc);
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
}
