package com.devknowledge.controller;

import com.devknowledge.dto.KbChunkSearchResult;
import com.devknowledge.dto.KbCreateRequest;
import com.devknowledge.model.KbDocument;
import com.devknowledge.model.KnowledgeBase;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.KbService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbController {

    private final KbService kbService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public Mono<ResponseEntity<KnowledgeBase>> createKb(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody KbCreateRequest req) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());
        return kbService.createKb(userId, req).map(ResponseEntity::ok);
    }

    @GetMapping
    public Mono<ResponseEntity<List<KnowledgeBase>>> getKbs(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());
        return kbService.getUserKbs(userId).map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<KnowledgeBase>> getKb(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return kbService.getKb(id, userId)
                .map(kb -> kb != null ? ResponseEntity.ok(kb) : ResponseEntity.status(403).build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteKb(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return kbService.deleteKb(id, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * 批量更新知识库排序（拖拽排序）
     */
    @PatchMapping("/reorder")
    public Mono<ResponseEntity<Void>> reorderKbs(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, List<UUID>> body) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());
        List<UUID> orderedIds = body.get("orderedIds");
        if (orderedIds == null || orderedIds.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().<Void>build());
        }
        return kbService.reorderKbs(userId, orderedIds)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    public Mono<ResponseEntity<KbDocument>> uploadDocument(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestPart("file") Mono<FilePart> file,
            @RequestParam(required = false) Integer minChunkSize,
            @RequestParam(required = false) Integer maxChunkSize) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());
        return file.flatMap(fp -> DataBufferUtils.join(fp.content())
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        })
                        .flatMap(bytes ->
                                kbService.uploadDocument(id, fp.filename(), bytes.length, bytes, minChunkSize, maxChunkSize)))
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/{id}/documents/batch", consumes = "multipart/form-data")
    public Mono<ResponseEntity<List<KbDocument>>> batchUpload(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestPart("files") Flux<FilePart> files,
            @RequestParam(required = false) Integer minChunkSize,
            @RequestParam(required = false) Integer maxChunkSize) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return files.take(10).flatMap(fp ->
                DataBufferUtils.join(fp.content())
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        })
                        .flatMap(bytes -> kbService.uploadDocument(id, fp.filename(), bytes.length, bytes, minChunkSize, maxChunkSize))
        ).collectList().map(ResponseEntity::ok);
    }

    @GetMapping("/{id}/documents")
    public Mono<ResponseEntity<List<KbDocument>>> getDocuments(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());
        return kbService.getDocuments(id).map(ResponseEntity::ok);
    }

    @DeleteMapping("/documents/{docId}")
    public Mono<ResponseEntity<Void>> deleteDocument(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID docId) {
        UUID userId = extractUserId(authHeader);
        return kbService.deleteDocument(docId, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * 重试文档解析
     * 解析失败后，根据文档 ID 重新触发解析（复用已存储的原始文件）
     */
    @PostMapping("/documents/{docId}/retry")
    public Mono<ResponseEntity<KbDocument>> retryDocument(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID docId) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());
        return kbService.retryDocument(docId, userId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/{id}/search")
    public Mono<ResponseEntity<List<KbDocument>>> searchKb(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam("q") String query) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());
        return kbService.searchKb(id, query).map(ResponseEntity::ok);
    }

    /**
     * BM25 关键词检索
     * 使用 Jieba 分词 + PostgreSQL tsvector 实现中文全文检索
     */
    @GetMapping("/{id}/search/bm25")
    public Mono<ResponseEntity<List<KbChunkSearchResult>>> searchByBm25(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "10") int topK) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());
        return kbService.searchByBm25(id, query, topK).map(ResponseEntity::ok);
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            return jwtTokenProvider.getUserId(authHeader.replace("Bearer ", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
