package com.devknowledge.controller;

import com.devknowledge.dto.WikiGraphResponse;
import com.devknowledge.dto.WikiUploadResponse;
import com.devknowledge.model.WikiIndex;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.WikiFileService;
import com.devknowledge.service.WikiGraphService;
import com.devknowledge.service.WikiIngestService;
import com.devknowledge.service.WikiLlmService;
import com.devknowledge.mapper.WikiEntityMapper;
import com.devknowledge.model.WikiEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Wiki 知识图谱 REST 控制器
 */
@RestController
@RequestMapping("/api/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiIngestService wikiIngestService;
    private final WikiFileService wikiFileService;
    private final WikiGraphService wikiGraphService;
    private final WikiLlmService wikiLlmService;
    private final WikiEntityMapper wikiEntityMapper;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 上传单个文档到 wiki
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Mono<ResponseEntity<WikiUploadResponse>> uploadDocument(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("file") Mono<FilePart> file) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return file.flatMap(fp -> DataBufferUtils.join(fp.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> wikiIngestService.ingestDocument(userId, fp.filename(), bytes))
                .map(doc -> {
                    WikiUploadResponse resp = new WikiUploadResponse();
                    resp.setDocId(doc.getId());
                    resp.setFilename(doc.getFilename());
                    resp.setStatus(doc.getStatus());
                    resp.setMessage("文档上传成功，正在处理");
                    return ResponseEntity.ok(resp);
                }));
    }

    /**
     * 上传 Obsidian vault 目录
     */
    @PostMapping(value = "/upload-vault", consumes = "multipart/form-data")
    public Mono<ResponseEntity<List<WikiUploadResponse>>> uploadVault(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("files") Flux<FilePart> files) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return files.flatMap(fp -> DataBufferUtils.join(fp.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new Object[]{fp.filename(), bytes};
                }))
                .filter(fileData -> ((String) fileData[0]).endsWith(".md"))
                .flatMap(fileData -> {
                    String filename = (String) fileData[0];
                    byte[] bytes = (byte[]) fileData[1];
                    return wikiIngestService.ingestDocument(userId, filename, bytes)
                            .map(doc -> {
                                WikiUploadResponse resp = new WikiUploadResponse();
                                resp.setDocId(doc.getId());
                                resp.setFilename(doc.getFilename());
                                resp.setStatus(doc.getStatus());
                                return resp;
                            });
                })
                .collectList()
                .map(ResponseEntity::ok);
    }

    /**
     * 从知识库导入文档
     */
    @PostMapping("/import/{kbDocId}")
    public Mono<ResponseEntity<WikiUploadResponse>> importFromKb(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID kbDocId,
            @RequestParam String filename,
            @RequestParam String content) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return wikiIngestService.importFromKb(userId, filename, content)
                .map(doc -> {
                    WikiUploadResponse resp = new WikiUploadResponse();
                    resp.setDocId(doc.getId());
                    resp.setFilename(doc.getFilename());
                    resp.setStatus(doc.getStatus());
                    resp.setMessage("导入成功");
                    return ResponseEntity.ok(resp);
                });
    }

    /**
     * 获取 wiki 页面列表（从索引表）
     */
    @GetMapping("/pages")
    public Mono<ResponseEntity<List<WikiIndex>>> getPages(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String category) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return wikiGraphService.getIndexEntries(userId, category)
                .map(ResponseEntity::ok);
    }

    /**
     * 读取 wiki 页面内容
     */
    @GetMapping("/page")
    public Mono<ResponseEntity<String>> getPage(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String path) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return wikiFileService.readPage(userId, path)
                .map(content -> content != null
                        ? ResponseEntity.ok(content)
                        : ResponseEntity.notFound().build());
    }

    /**
     * 获取图谱数据（实体 + 关系）
     */
    @GetMapping("/graph")
    public Mono<ResponseEntity<WikiGraphResponse>> getGraph(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return wikiGraphService.getGraphData(userId)
                .map(ResponseEntity::ok);
    }

    /**
     * 手动触发深度分析
     */
    @PostMapping("/analyze/{docId}")
    public Mono<ResponseEntity<WikiUploadResponse>> analyzeDocument(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID docId) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return wikiIngestService.deepAnalyze(userId, docId)
                .map(doc -> {
                    WikiUploadResponse resp = new WikiUploadResponse();
                    resp.setDocId(doc.getId());
                    resp.setFilename(doc.getFilename());
                    resp.setStatus("analyzed");
                    resp.setMessage("深度分析完成");
                    return ResponseEntity.ok(resp);
                })
                .onErrorResume(e -> {
                    WikiUploadResponse resp = new WikiUploadResponse();
                    resp.setStatus("error");
                    resp.setMessage("分析失败: " + e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(resp));
                });
    }

    /**
     * Wiki 健康检查
     */
    @PostMapping("/lint")
    public Mono<ResponseEntity<WikiLlmService.LintResult>> lintWiki(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return Mono.zip(
                wikiGraphService.getIndexEntries(userId, null),
                Mono.fromCallable(() -> wikiEntityMapper.selectList(
                        new LambdaQueryWrapper<WikiEntity>().eq(WikiEntity::getUserId, userId)))
                        .subscribeOn(Schedulers.boundedElastic())
        ).flatMap(tuple -> {
            String pagesSummary = tuple.getT1().stream()
                    .map(p -> "- " + p.getTitle() + " (" + p.getCategory() + ")")
                    .collect(Collectors.joining("\n"));
            String entitiesSummary = tuple.getT2().stream()
                    .map(e -> "- " + e.getName() + " (" + e.getType() + ")")
                    .collect(Collectors.joining("\n"));
            return wikiLlmService.lintWiki(userId, pagesSummary, entitiesSummary);
        }).map(ResponseEntity::ok);
    }

    /**
     * 删除 wiki 文档及相关内容
     */
    @DeleteMapping("/doc/{docId}")
    public Mono<ResponseEntity<Void>> deleteDocument(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID docId) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) return Mono.just(ResponseEntity.status(401).build());

        return wikiIngestService.deleteDocument(userId, docId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        return jwtTokenProvider.getUserId(token);
    }
}
