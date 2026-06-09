package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.mapper.WikiDocumentMapper;
import com.devknowledge.mapper.WikiEntityMapper;
import com.devknowledge.mapper.WikiIndexMapper;
import com.devknowledge.mapper.WikiRelationMapper;
import com.devknowledge.model.WikiDocument;
import com.devknowledge.model.WikiEntity;
import com.devknowledge.model.WikiIndex;
import com.devknowledge.model.WikiRelation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Wiki 文档摄取服务
 * 负责文档存储、LLM 分析、wiki 页面生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiIngestService {

    private final WikiDocumentMapper wikiDocumentMapper;
    private final WikiEntityMapper wikiEntityMapper;
    private final WikiRelationMapper wikiRelationMapper;
    private final WikiIndexMapper wikiIndexMapper;
    private final WikiFileService wikiFileService;
    private final WikiLlmService wikiLlmService;

    /**
     * 摄取文档：存储原始文件 + LLM 分析 + 生成 wiki 页面
     */
    public Mono<WikiDocument> ingestDocument(UUID userId, String filename, byte[] fileBytes) {
        return Mono.fromCallable(() -> {
            // 初始化 vault
            wikiFileService.initUserVault(userId).block();

            // 解析文件内容
            String content = new String(fileBytes, StandardCharsets.UTF_8);
            String fileType = extractFileType(filename);

            // 存储文档记录
            WikiDocument doc = new WikiDocument();
            doc.setId(UUID.randomUUID());
            doc.setUserId(userId);
            doc.setFilename(filename);
            doc.setFileType(fileType);
            doc.setFileSize((long) fileBytes.length);
            doc.setContent(content);
            doc.setStatus("processing");
            doc.setSourceType("upload");
            doc.setCreatedAt(Instant.now());
            wikiDocumentMapper.insert(doc);

            return doc;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(doc -> {
            // LLM 分析
            return wikiLlmService.analyzeEntities(userId, doc.getContent(), doc.getFilename())
                    .flatMap(analysis -> processAnalysisResult(userId, doc, analysis))
                    .onErrorResume(e -> {
                        log.error("文档分析失败: {}", e.getMessage());
                        doc.setStatus("error");
                        doc.setErrorMsg(e.getMessage());
                        return Mono.fromCallable(() -> {
                            wikiDocumentMapper.updateById(doc);
                            return doc;
                        }).subscribeOn(Schedulers.boundedElastic());
                    });
        });
    }

    /**
     * 从知识库导入文档
     */
    public Mono<WikiDocument> importFromKb(UUID userId, String filename, String content) {
        return ingestDocument(userId, filename, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 深度分析：重新提取实体和关系
     */
    public Mono<WikiDocument> deepAnalyze(UUID userId, UUID docId) {
        return Mono.fromCallable(() -> {
            WikiDocument doc = wikiDocumentMapper.selectById(docId);
            if (doc == null || !doc.getUserId().equals(userId)) {
                throw new RuntimeException("文档不存在或无权限");
            }
            return doc;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(doc -> wikiLlmService.analyzeEntities(userId, doc.getContent(), doc.getFilename())
                .flatMap(analysis -> {
                    // 清除旧的实体和关系
                    cleanupEntitiesAndRelations(userId, docId);

                    // 处理新的分析结果
                    return processAnalysisResult(userId, doc, analysis);
                })
        );
    }

    /**
     * 删除文档及相关 wiki 内容
     */
    public Mono<Void> deleteDocument(UUID userId, UUID docId) {
        return Mono.fromRunnable(() -> {
            // 查询相关实体
            var entities = wikiEntityMapper.selectList(
                    new LambdaQueryWrapper<WikiEntity>()
                            .eq(WikiEntity::getUserId, userId)
                            .eq(WikiEntity::getDocId, docId));

            // 删除相关关系
            for (WikiEntity entity : entities) {
                wikiRelationMapper.delete(
                        new LambdaQueryWrapper<WikiRelation>()
                                .eq(WikiRelation::getUserId, userId)
                                .and(w -> w.eq(WikiRelation::getSourceId, entity.getId())
                                        .or().eq(WikiRelation::getTargetId, entity.getId())));
            }

            // 删除实体
            wikiEntityMapper.delete(
                    new LambdaQueryWrapper<WikiEntity>()
                            .eq(WikiEntity::getUserId, userId)
                            .eq(WikiEntity::getDocId, docId));

            // 删除索引（过滤包含该 docId 的条目）
            wikiIndexMapper.selectList(
                    new LambdaQueryWrapper<WikiIndex>().eq(WikiIndex::getUserId, userId))
                    .stream()
                    .filter(idx -> {
                        String[] docIds = idx.getDocIds();
                        return docIds != null && Arrays.asList(docIds).contains(docId.toString());
                    })
                    .forEach(idx -> wikiIndexMapper.deleteById(idx.getId()));

            // 删除文档记录
            wikiDocumentMapper.deleteById(docId);

            log.info("删除 wiki 文档: {}", docId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 处理 LLM 分析结果：创建实体、关系、索引、页面
     */
    private Mono<WikiDocument> processAnalysisResult(UUID userId, WikiDocument doc,
                                                       WikiLlmService.AnalysisResult analysis) {
        return Mono.fromRunnable(() -> {
            // 生成来源摘要页面
            String summaryPath = "sources/" + sanitizeFilename(doc.getFilename()) + "-summary.md";
            String summaryContent = generateSummaryPage(doc.getFilename(), analysis.getSummary(), doc.getId());
            wikiFileService.writePage(userId, summaryPath, summaryContent).block();

            // 添加来源索引
            WikiIndex sourceIndex = new WikiIndex();
            sourceIndex.setId(UUID.randomUUID());
            sourceIndex.setUserId(userId);
            sourceIndex.setPagePath(summaryPath);
            sourceIndex.setTitle(doc.getFilename());
            sourceIndex.setCategory("source");
            sourceIndex.setTags(new String[]{doc.getFileType()});
            sourceIndex.setSummary(analysis.getSummary());
            sourceIndex.setDocIds(new String[]{doc.getId().toString()});
            sourceIndex.setUpdatedAt(Instant.now());
            wikiIndexMapper.insert(sourceIndex);

            // 创建实体页面和索引
            Map<String, UUID> entityNameToId = new HashMap<>();
            for (WikiLlmService.EntityInfo entity : analysis.getEntities()) {
                String entityPath = "entities/" + entity.getName() + ".md";
                String entityPage = generateEntityPage(entity, doc.getId());
                wikiFileService.writePage(userId, entityPath, entityPage).block();

                WikiEntity wikiEntity = new WikiEntity();
                wikiEntity.setId(UUID.randomUUID());
                wikiEntity.setUserId(userId);
                wikiEntity.setName(entity.getName());
                wikiEntity.setType(entity.getType());
                wikiEntity.setDescription(entity.getDescription());
                wikiEntity.setPagePath(entityPath);
                wikiEntity.setDocId(doc.getId());
                wikiEntity.setCreatedAt(Instant.now());
                wikiEntity.setUpdatedAt(Instant.now());
                wikiEntityMapper.insert(wikiEntity);
                entityNameToId.put(entity.getName(), wikiEntity.getId());

                // 添加索引
                WikiIndex entityIndex = new WikiIndex();
                entityIndex.setId(UUID.randomUUID());
                entityIndex.setUserId(userId);
                entityIndex.setPagePath(entityPath);
                entityIndex.setTitle(entity.getName());
                entityIndex.setCategory("entity");
                entityIndex.setTags(new String[]{entity.getType()});
                entityIndex.setSummary(entity.getDescription());
                entityIndex.setDocIds(new String[]{doc.getId().toString()});
                entityIndex.setUpdatedAt(Instant.now());
                wikiIndexMapper.insert(entityIndex);
            }

            // 存储关系
            for (WikiLlmService.RelationInfo rel : analysis.getRelations()) {
                UUID sourceId = entityNameToId.get(rel.getSource());
                UUID targetId = entityNameToId.get(rel.getTarget());
                if (sourceId != null && targetId != null) {
                    WikiRelation relation = new WikiRelation();
                    relation.setId(UUID.randomUUID());
                    relation.setUserId(userId);
                    relation.setSourceId(sourceId);
                    relation.setTargetId(targetId);
                    relation.setRelation(rel.getRelation());
                    relation.setDescription(rel.getDescription());
                    relation.setStrength(rel.getStrength());
                    relation.setCreatedAt(Instant.now());
                    wikiRelationMapper.insert(relation);
                }
            }

            // 更新文档状态
            doc.setStatus("ready");
            doc.setContent(null); // 清除内存中的内容
            wikiDocumentMapper.updateById(doc);

            // 写入 log
            appendToLog(userId, "ingest", doc.getFilename(),
                    "文档类型: " + doc.getFileType() +
                    "\n- 提取实体: " + analysis.getEntities().size() + " 个" +
                    "\n- 建立关系: " + analysis.getRelations().size() + " 条");

            log.info("文档摄取完成: {} -> {} 实体, {} 关系",
                    doc.getFilename(), analysis.getEntities().size(), analysis.getRelations().size());
        }).subscribeOn(Schedulers.boundedElastic()).then(Mono.just(doc));
    }

    /**
     * 清除旧的实体和关系
     */
    private void cleanupEntitiesAndRelations(UUID userId, UUID docId) {
        var oldEntities = wikiEntityMapper.selectList(
                new LambdaQueryWrapper<WikiEntity>()
                        .eq(WikiEntity::getUserId, userId)
                        .eq(WikiEntity::getDocId, docId));
        for (WikiEntity old : oldEntities) {
            wikiRelationMapper.delete(
                    new LambdaQueryWrapper<WikiRelation>()
                            .eq(WikiRelation::getUserId, userId)
                            .and(w -> w.eq(WikiRelation::getSourceId, old.getId())
                                    .or().eq(WikiRelation::getTargetId, old.getId())));
        }
        wikiEntityMapper.delete(
                new LambdaQueryWrapper<WikiEntity>()
                        .eq(WikiEntity::getUserId, userId)
                        .eq(WikiEntity::getDocId, docId));
    }

    private String extractFileType(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "txt";
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5._-]", "-")
                .replaceAll("-+", "-")
                .toLowerCase();
    }

    private String generateSummaryPage(String filename, String summary, UUID docId) {
        return "---\n" +
                "type: source\n" +
                "category: summary\n" +
                "sources: [" + docId + "]\n" +
                "created: " + Instant.now().toString().substring(0, 10) + "\n" +
                "---\n\n" +
                "# " + filename + "\n\n" +
                "> 来源文档摘要\n\n" +
                "## 摘要\n\n" +
                summary + "\n";
    }

    private String generateEntityPage(WikiLlmService.EntityInfo entity, UUID docId) {
        return "---\n" +
                "type: entity\n" +
                "category: " + entity.getType() + "\n" +
                "sources: [" + docId + "]\n" +
                "created: " + Instant.now().toString().substring(0, 10) + "\n" +
                "---\n\n" +
                "# " + entity.getName() + "\n\n" +
                "> " + entity.getDescription() + "\n\n" +
                "## 相关链接\n\n" +
                "- 来源文档\n";
    }

    private void appendToLog(UUID userId, String action, String title, String details) {
        String logEntry = "\n## [" + Instant.now().toString().substring(0, 16).replace("T", " ") + "] " +
                action + " | " + title + "\n- " + details + "\n";

        wikiFileService.readPage(userId, "log.md")
                .defaultIfEmpty("# Wiki Log\n")
                .flatMap(existing -> wikiFileService.writePage(userId, "log.md", existing + logEntry))
                .block();
    }
}
