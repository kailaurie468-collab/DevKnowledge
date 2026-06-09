# Wiki 知识图谱 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现基于 Karpathy LLM Wiki 模式的知识图谱功能，支持文档上传、LLM 自动生成 wiki 页面、实体关系提取、3D 图谱可视化。

**Architecture:** 后端 Spring Boot WebFlux + PostgreSQL 存储实体/关系，文件系统存储 wiki markdown 页面。前端 React + Three.js 3D 图谱。分 5 个阶段交付。

**Tech Stack:** Spring Boot WebFlux, MyBatis Plus, PostgreSQL, React 19, Three.js, @react-three/fiber, Tailwind CSS

---

## 文件结构总览

### 后端新增文件

| 文件路径 | 职责 |
|---------|------|
| `backend/src/main/resources/db/migration/V10__create_wiki_tables.sql` | 数据库迁移 |
| `backend/src/main/java/com/devknowledge/model/WikiDocument.java` | Wiki 文档实体 |
| `backend/src/main/java/com/devknowledge/model/WikiEntity.java` | Wiki 实体 |
| `backend/src/main/java/com/devknowledge/model/WikiRelation.java` | Wiki 关系 |
| `backend/src/main/java/com/devknowledge/model/WikiIndex.java` | Wiki 索引 |
| `backend/src/main/java/com/devknowledge/mapper/WikiDocumentMapper.java` | 文档 Mapper |
| `backend/src/main/java/com/devknowledge/mapper/WikiEntityMapper.java` | 实体 Mapper |
| `backend/src/main/java/com/devknowledge/mapper/WikiRelationMapper.java` | 关系 Mapper |
| `backend/src/main/java/com/devknowledge/mapper/WikiIndexMapper.java` | 索引 Mapper |
| `backend/src/main/java/com/devknowledge/dto/WikiUploadResponse.java` | 上传响应 DTO |
| `backend/src/main/java/com/devknowledge/dto/WikiGraphResponse.java` | 图谱数据 DTO |
| `backend/src/main/java/com/devknowledge/service/WikiFileService.java` | 文件操作 |
| `backend/src/main/java/com/devknowledge/service/WikiIngestService.java` | 文档摄取 |
| `backend/src/main/java/com/devknowledge/service/WikiLlmService.java` | LLM 分析 |
| `backend/src/main/java/com/devknowledge/service/WikiGraphService.java` | 图谱查询 |
| `backend/src/main/java/com/devknowledge/service/WikiRetrievalService.java` | Demo 检索 |
| `backend/src/main/java/com/devknowledge/controller/WikiController.java` | REST 控制器 |

### 前端新增文件

| 文件路径 | 职责 |
|---------|------|
| `frontend/src/api/wiki.ts` | Wiki API 客户端 |
| `frontend/src/types/wiki.ts` | Wiki 类型定义 |
| `frontend/src/pages/WikiPage.tsx` | Wiki 主页面 |
| `frontend/src/components/wiki/WikiSidebar.tsx` | 侧边栏组件 |
| `frontend/src/components/wiki/WikiContent.tsx` | 内容渲染组件 |
| `frontend/src/components/wiki/WikiUpload.tsx` | 上传组件 |
| `frontend/src/components/wiki/WikiGraph3D.tsx` | 3D 图谱组件 |
| `frontend/src/components/wiki/GraphNode.tsx` | 图谱节点组件 |

### 前端修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `frontend/src/App.tsx` | 添加 /wiki 路由 |
| `frontend/src/pages/HomePage.tsx` | 添加 Wiki 入口卡片 |
| `frontend/src/pages/DemoPage.tsx` | 添加 Wiki 检索源选项 |
| `frontend/src/types/api.ts` | 添加 Wiki 相关类型 |

---

## Phase 3c-1: 基础设施

### Task 1: 数据库迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__create_wiki_tables.sql`

- [ ] **Step 1: 创建迁移文件**

```sql
-- V10__create_wiki_tables.sql
-- Wiki 知识图谱表结构

-- Wiki 原始文档表
CREATE TABLE wiki_documents (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    filename    VARCHAR(255) NOT NULL,
    file_type   VARCHAR(20) NOT NULL,
    file_size   BIGINT NOT NULL,
    content     TEXT,
    status      VARCHAR(20) DEFAULT 'processing',
    error_msg   TEXT,
    source_type VARCHAR(20) DEFAULT 'upload',
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- Wiki 实体表
CREATE TABLE wiki_entities (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(50) NOT NULL,
    description TEXT,
    page_path   VARCHAR(500),
    doc_id      UUID,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now()
);

-- Wiki 关系表
CREATE TABLE wiki_relations (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    source_id   UUID NOT NULL,
    target_id   UUID NOT NULL,
    relation    VARCHAR(100) NOT NULL,
    description TEXT,
    strength    DOUBLE PRECISION DEFAULT 1.0,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- Wiki 索引表
CREATE TABLE wiki_index (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    page_path   VARCHAR(500) NOT NULL,
    title       VARCHAR(300) NOT NULL,
    category    VARCHAR(50) NOT NULL,
    tags        TEXT[] DEFAULT '{}',
    summary     TEXT,
    doc_ids     UUID[] DEFAULT '{}',
    updated_at  TIMESTAMPTZ DEFAULT now()
);

-- 索引
CREATE INDEX idx_wiki_documents_user ON wiki_documents(user_id);
CREATE INDEX idx_wiki_documents_status ON wiki_documents(user_id, status);
CREATE INDEX idx_wiki_entities_user ON wiki_entities(user_id);
CREATE INDEX idx_wiki_entities_type ON wiki_entities(user_id, type);
CREATE INDEX idx_wiki_entities_doc ON wiki_entities(doc_id);
CREATE INDEX idx_wiki_relations_source ON wiki_relations(source_id);
CREATE INDEX idx_wiki_relations_target ON wiki_relations(target_id);
CREATE INDEX idx_wiki_index_user ON wiki_index(user_id);
CREATE INDEX idx_wiki_index_category ON wiki_index(user_id, category);
```

- [ ] **Step 2: 验证迁移文件语法**

确认 SQL 语法正确，无外键约束。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V10__create_wiki_tables.sql
git commit -m "feat(wiki): V10 数据库迁移 - wiki 知识图谱表结构"
```

---

### Task 2: 后端 Model 实体类

**Files:**
- Create: `backend/src/main/java/com/devknowledge/model/WikiDocument.java`
- Create: `backend/src/main/java/com/devknowledge/model/WikiEntity.java`
- Create: `backend/src/main/java/com/devknowledge/model/WikiRelation.java`
- Create: `backend/src/main/java/com/devknowledge/model/WikiIndex.java`

- [ ] **Step 1: 创建 WikiDocument 实体**

```java
package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("wiki_documents")
public class WikiDocument {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    private String filename;
    private String fileType;
    private Long fileSize;
    private String content;
    private String status;
    private String errorMsg;
    private String sourceType;
    private Instant createdAt;
}
```

- [ ] **Step 2: 创建 WikiEntity 实体**

```java
package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("wiki_entities")
public class WikiEntity {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    private String name;
    private String type;
    private String description;
    private String pagePath;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID docId;

    private Instant createdAt;
    private Instant updatedAt;
}
```

- [ ] **Step 3: 创建 WikiRelation 实体**

```java
package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("wiki_relations")
public class WikiRelation {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID sourceId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID targetId;

    private String relation;
    private String description;
    private Double strength;
    private Instant createdAt;
}
```

- [ ] **Step 4: 创建 WikiIndex 实体**

```java
package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("wiki_index")
public class WikiIndex {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    private String pagePath;
    private String title;
    private String category;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;

    private String summary;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] docIds;

    private Instant updatedAt;
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devknowledge/model/Wiki*.java
git commit -m "feat(wiki): Model 实体类 - WikiDocument/WikiEntity/WikiRelation/WikiIndex"
```

---

### Task 3: 后端 Mapper 接口

**Files:**
- Create: `backend/src/main/java/com/devknowledge/mapper/WikiDocumentMapper.java`
- Create: `backend/src/main/java/com/devknowledge/mapper/WikiEntityMapper.java`
- Create: `backend/src/main/java/com/devknowledge/mapper/WikiRelationMapper.java`
- Create: `backend/src/main/java/com/devknowledge/mapper/WikiIndexMapper.java`

- [ ] **Step 1: 创建 WikiDocumentMapper**

```java
package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.WikiDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiDocumentMapper extends BaseMapper<WikiDocument> {
}
```

- [ ] **Step 2: 创建 WikiEntityMapper**

```java
package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.WikiEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiEntityMapper extends BaseMapper<WikiEntity> {
}
```

- [ ] **Step 3: 创建 WikiRelationMapper**

```java
package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.WikiRelation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiRelationMapper extends BaseMapper<WikiRelation> {
}
```

- [ ] **Step 4: 创建 WikiIndexMapper**

```java
package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.WikiIndex;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiIndexMapper extends BaseMapper<WikiIndex> {
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devknowledge/mapper/Wiki*Mapper.java
git commit -m "feat(wiki): Mapper 接口 - WikiDocument/WikiEntity/WikiRelation/WikiIndex"
```

---

### Task 4: 后端 DTO 类

**Files:**
- Create: `backend/src/main/java/com/devknowledge/dto/WikiUploadResponse.java`
- Create: `backend/src/main/java/com/devknowledge/dto/WikiGraphResponse.java`

- [ ] **Step 1: 创建 WikiUploadResponse**

```java
package com.devknowledge.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class WikiUploadResponse {
    private UUID docId;
    private String filename;
    private String status;
    private String message;
}
```

- [ ] **Step 2: 创建 WikiGraphResponse**

```java
package com.devknowledge.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class WikiGraphResponse {
    private List<EntityNode> entities;
    private List<RelationEdge> relations;

    @Data
    public static class EntityNode {
        private UUID id;
        private String name;
        private String type;
        private String description;
        private String pagePath;
    }

    @Data
    public static class RelationEdge {
        private UUID sourceId;
        private UUID targetId;
        private String relation;
        private String description;
        private Double strength;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/dto/Wiki*.java
git commit -m "feat(wiki): DTO 类 - WikiUploadResponse/WikiGraphResponse"
```

---

### Task 5: WikiFileService 文件操作服务

**Files:**
- Create: `backend/src/main/java/com/devknowledge/service/WikiFileService.java`

- [ ] **Step 1: 创建 WikiFileService**

```java
package com.devknowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Service
public class WikiFileService {

    @Value("${wiki.vault.path:wiki_vault}")
    private String vaultBasePath;

    /**
     * 获取用户 wiki vault 根目录
     */
    public Path getUserVaultPath(UUID userId) {
        return Path.of(vaultBasePath, userId.toString());
    }

    /**
     * 初始化用户 vault 目录结构
     */
    public Mono<Void> initUserVault(UUID userId) {
        return Mono.fromRunnable(() -> {
            try {
                Path vault = getUserVaultPath(userId);
                Files.createDirectories(vault.resolve("entities"));
                Files.createDirectories(vault.resolve("concepts"));
                Files.createDirectories(vault.resolve("sources"));
                Files.createDirectories(vault.resolve("comparisons"));
                log.info("初始化 wiki vault: {}", vault);
            } catch (IOException e) {
                throw new RuntimeException("初始化 vault 失败", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 写入 wiki 页面文件
     */
    public Mono<Void> writePage(UUID userId, String relativePath, String content) {
        return Mono.fromRunnable(() -> {
            try {
                Path filePath = getUserVaultPath(userId).resolve(relativePath);
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, content);
                log.info("写入 wiki 页面: {}", filePath);
            } catch (IOException e) {
                throw new RuntimeException("写入页面失败: " + relativePath, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 读取 wiki 页面文件
     */
    public Mono<String> readPage(UUID userId, String relativePath) {
        return Mono.fromCallable(() -> {
            Path filePath = getUserVaultPath(userId).resolve(relativePath);
            if (!Files.exists(filePath)) {
                return null;
            }
            return Files.readString(filePath);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 删除 wiki 页面文件
     */
    public Mono<Void> deletePage(UUID userId, String relativePath) {
        return Mono.fromRunnable(() -> {
            try {
                Path filePath = getUserVaultPath(userId).resolve(relativePath);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.info("删除 wiki 页面: {}", filePath);
                }
            } catch (IOException e) {
                throw new RuntimeException("删除页面失败: " + relativePath, e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 检查页面是否存在
     */
    public Mono<Boolean> pageExists(UUID userId, String relativePath) {
        return Mono.fromCallable(() -> {
            Path filePath = getUserVaultPath(userId).resolve(relativePath);
            return Files.exists(filePath);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
```

- [ ] **Step 2: 添加配置项**

在 `application.yml` 中添加：

```yaml
wiki:
  vault:
    path: wiki_vault
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/WikiFileService.java
git commit -m "feat(wiki): WikiFileService - vault 目录管理和文件读写"
```

---

### Task 6: WikiController 基础端点

**Files:**
- Create: `backend/src/main/java/com/devknowledge/controller/WikiController.java`

- [ ] **Step 1: 创建 WikiController（基础部分）**

```java
package com.devknowledge.controller;

import com.devknowledge.dto.WikiUploadResponse;
import com.devknowledge.model.WikiDocument;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.WikiFileService;
import com.devknowledge.service.WikiIngestService;
import com.devknowledge.service.WikiGraphService;
import com.devknowledge.dto.WikiGraphResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiIngestService wikiIngestService;
    private final WikiFileService wikiFileService;
    private final WikiGraphService wikiGraphService;
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
     * 获取 wiki 页面列表（从索引表）
     */
    @GetMapping("/pages")
    public Mono<ResponseEntity<?>> getPages(
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
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/devknowledge/controller/WikiController.java
git commit -m "feat(wiki): WikiController - 上传/页面/图谱/删除端点"
```

---

### Task 7: WikiIngestService 基础摄取

**Files:**
- Create: `backend/src/main/java/com/devknowledge/service/WikiIngestService.java`

- [ ] **Step 1: 创建 WikiIngestService（基础部分）**

```java
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WikiIngestService {

    private final WikiDocumentMapper wikiDocumentMapper;
    private final WikiEntityMapper wikiEntityMapper;
    private final WikiRelationMapper wikiRelationMapper;
    private final WikiIndexMapper wikiIndexMapper;
    private final WikiFileService wikiFileService;

    /**
     * 摄取文档：存储原始文件 + 生成基础 wiki 页面
     */
    public Mono<WikiDocument> ingestDocument(UUID userId, String filename, byte[] fileBytes) {
        return Mono.fromCallable(() -> {
            // 1. 初始化 vault
            wikiFileService.initUserVault(userId).block();

            // 2. 解析文件内容
            String content = new String(fileBytes, StandardCharsets.UTF_8);
            String fileType = extractFileType(filename);

            // 3. 存储文档记录
            WikiDocument doc = new WikiDocument();
            doc.setId(UUID.randomUUID());
            doc.setUserId(userId);
            doc.setFilename(filename);
            doc.setFileType(fileType);
            doc.setFileSize((long) fileBytes.length);
            doc.setContent(content);
            doc.setStatus("ready");
            doc.setSourceType("upload");
            doc.setCreatedAt(Instant.now());
            wikiDocumentMapper.insert(doc);

            // 4. 生成来源摘要页面
            String summaryPath = "sources/" + sanitizeFilename(filename) + "-summary.md";
            String summaryContent = generateSummaryPage(filename, content, doc.getId());
            wikiFileService.writePage(userId, summaryPath, summaryContent).block();

            // 5. 更新索引
            WikiIndex index = new WikiIndex();
            index.setId(UUID.randomUUID());
            index.setUserId(userId);
            index.setPagePath(summaryPath);
            index.setTitle(filename);
            index.setCategory("source");
            index.setTags(new String[]{fileType});
            index.setSummary("来源文档: " + filename);
            index.setDocIds(new String[]{doc.getId().toString()});
            index.setUpdatedAt(Instant.now());
            wikiIndexMapper.insert(index);

            // 6. 写入 log
            appendToLog(userId, "ingest", filename, "文档类型: " + fileType);

            log.info("文档摄取完成: {} -> {}", filename, summaryPath);
            return doc;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 删除文档及相关 wiki 内容
     */
    public Mono<Void> deleteDocument(UUID userId, UUID docId) {
        return Mono.fromRunnable(() -> {
            // 1. 查询相关实体
            var entities = wikiEntityMapper.selectList(
                    new LambdaQueryWrapper<WikiEntity>().eq(WikiEntity::getDocId, docId));

            // 2. 删除相关关系
            for (WikiEntity entity : entities) {
                wikiRelationMapper.delete(
                        new LambdaQueryWrapper<WikiRelation>()
                                .eq(WikiRelation::getSourceId, entity.getId())
                                .or()
                                .eq(WikiRelation::getTargetId, entity.getId()));
            }

            // 3. 删除实体
            wikiEntityMapper.delete(
                    new LambdaQueryWrapper<WikiEntity>().eq(WikiEntity::getDocId, docId));

            // 4. 删除索引
            wikiIndexMapper.selectList(
                    new LambdaQueryWrapper<WikiIndex>().eq(WikiIndex::getUserId, userId))
                    .stream()
                    .filter(idx -> {
                        String[] docIds = idx.getDocIds();
                        if (docIds == null) return false;
                        for (String id : docIds) {
                            if (id.equals(docId.toString())) return true;
                        }
                        return false;
                    })
                    .forEach(idx -> wikiIndexMapper.deleteById(idx.getId()));

            // 5. 删除文档记录
            wikiDocumentMapper.deleteById(docId);

            log.info("删除 wiki 文档: {}", docId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
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

    private String generateSummaryPage(String filename, String content, UUID docId) {
        // 截取前 500 字符作为预览
        String preview = content.length() > 500 ? content.substring(0, 500) + "..." : content;

        return "---\n" +
                "type: source\n" +
                "category: summary\n" +
                "sources: [" + docId + "]\n" +
                "created: " + Instant.now().toString().substring(0, 10) + "\n" +
                "---\n\n" +
                "# " + filename + "\n\n" +
                "> 来源文档摘要\n\n" +
                "## 内容预览\n\n" +
                preview + "\n";
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
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/WikiIngestService.java
git commit -m "feat(wiki): WikiIngestService - 文档摄取基础流程"
```

---

### Task 8: WikiGraphService 图谱查询

**Files:**
- Create: `backend/src/main/java/com/devknowledge/service/WikiGraphService.java`

- [ ] **Step 1: 创建 WikiGraphService**

```java
package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.WikiGraphResponse;
import com.devknowledge.mapper.WikiEntityMapper;
import com.devknowledge.mapper.WikiIndexMapper;
import com.devknowledge.mapper.WikiRelationMapper;
import com.devknowledge.model.WikiEntity;
import com.devknowledge.model.WikiIndex;
import com.devknowledge.model.WikiRelation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WikiGraphService {

    private final WikiEntityMapper wikiEntityMapper;
    private final WikiRelationMapper wikiRelationMapper;
    private final WikiIndexMapper wikiIndexMapper;

    /**
     * 获取图谱数据（实体 + 关系）
     */
    public Mono<WikiGraphResponse> getGraphData(UUID userId) {
        return Mono.fromCallable(() -> {
            // 查询实体
            List<WikiEntity> entities = wikiEntityMapper.selectList(
                    new LambdaQueryWrapper<WikiEntity>().eq(WikiEntity::getUserId, userId));

            // 查询关系
            List<UUID> entityIds = entities.stream()
                    .map(WikiEntity::getId)
                    .collect(Collectors.toList());

            List<WikiRelation> relations = entityIds.isEmpty() ? List.of() :
                    wikiRelationMapper.selectList(
                            new LambdaQueryWrapper<WikiRelation>()
                                    .eq(WikiRelation::getUserId, userId));

            // 构建响应
            WikiGraphResponse response = new WikiGraphResponse();
            response.setEntities(entities.stream().map(e -> {
                WikiGraphResponse.EntityNode node = new WikiGraphResponse.EntityNode();
                node.setId(e.getId());
                node.setName(e.getName());
                node.setType(e.getType());
                node.setDescription(e.getDescription());
                node.setPagePath(e.getPagePath());
                return node;
            }).collect(Collectors.toList()));

            response.setRelations(relations.stream().map(r -> {
                WikiGraphResponse.RelationEdge edge = new WikiGraphResponse.RelationEdge();
                edge.setSourceId(r.getSourceId());
                edge.setTargetId(r.getTargetId());
                edge.setRelation(r.getRelation());
                edge.setDescription(r.getDescription());
                edge.setStrength(r.getStrength());
                return edge;
            }).collect(Collectors.toList()));

            return response;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取索引条目列表
     */
    public Mono<List<WikiIndex>> getIndexEntries(UUID userId, String category) {
        return Mono.fromCallable(() -> {
            LambdaQueryWrapper<WikiIndex> wrapper = new LambdaQueryWrapper<WikiIndex>()
                    .eq(WikiIndex::getUserId, userId);
            if (category != null && !category.isEmpty()) {
                wrapper.eq(WikiIndex::getCategory, category);
            }
            wrapper.orderByDesc(WikiIndex::getUpdatedAt);
            return wikiIndexMapper.selectList(wrapper);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/WikiGraphService.java
git commit -m "feat(wiki): WikiGraphService - 图谱数据查询"
```

---

### Task 9: 前端类型定义和 API 客户端

**Files:**
- Create: `frontend/src/types/wiki.ts`
- Create: `frontend/src/api/wiki.ts`

- [ ] **Step 1: 创建 wiki.ts 类型定义**

```typescript
// frontend/src/types/wiki.ts

export interface WikiDocument {
  id: string
  userId: string
  filename: string
  fileType: string
  fileSize: number
  status: 'processing' | 'ready' | 'error'
  errorMsg?: string
  sourceType: 'upload' | 'obsidian_vault' | 'kb_import'
  createdAt: string
}

export interface WikiEntity {
  id: string
  userId: string
  name: string
  type: 'concept' | 'framework' | 'api' | 'tool'
  description?: string
  pagePath?: string
  docId?: string
  createdAt: string
  updatedAt: string
}

export interface WikiRelation {
  id: string
  userId: string
  sourceId: string
  targetId: string
  relation: 'uses' | 'extends' | 'contradicts' | 'related_to'
  description?: string
  strength: number
  createdAt: string
}

export interface WikiIndexEntry {
  id: string
  userId: string
  pagePath: string
  title: string
  category: 'entity' | 'concept' | 'source' | 'summary'
  tags: string[]
  summary?: string
  docIds: string[]
  updatedAt: string
}

export interface WikiGraphData {
  entities: WikiGraphNode[]
  relations: WikiGraphEdge[]
}

export interface WikiGraphNode {
  id: string
  name: string
  type: string
  description?: string
  pagePath?: string
}

export interface WikiGraphEdge {
  sourceId: string
  targetId: string
  relation: string
  description?: string
  strength: number
}

export interface WikiUploadResponse {
  docId: string
  filename: string
  status: string
  message: string
}
```

- [ ] **Step 2: 创建 wiki.ts API 客户端**

```typescript
// frontend/src/api/wiki.ts
import { api } from './client'
import type {
  WikiUploadResponse,
  WikiIndexEntry,
  WikiGraphData,
} from '@/types/wiki'

export const wikiApi = {
  /**
   * 上传单个文档
   */
  uploadDocument: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    const token = localStorage.getItem('accessToken')
    return fetch('/api/wiki/upload', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    }).then(res => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json() as Promise<WikiUploadResponse>
    })
  },

  /**
   * 获取页面列表
   */
  getPages: (category?: string) => {
    const params = category ? `?category=${category}` : ''
    return api.get<WikiIndexEntry[]>(`/wiki/pages${params}`)
  },

  /**
   * 读取页面内容
   */
  getPage: (path: string) => {
    const token = localStorage.getItem('accessToken')
    return fetch(`/api/wiki/page?path=${encodeURIComponent(path)}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    }).then(res => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.text()
    })
  },

  /**
   * 获取图谱数据
   */
  getGraph: () =>
    api.get<WikiGraphData>('/wiki/graph'),

  /**
   * 删除文档
   */
  deleteDocument: (docId: string) =>
    api.delete<void>(`/wiki/doc/${docId}`),
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/wiki.ts frontend/src/api/wiki.ts
git commit -m "feat(wiki): 前端类型定义和 API 客户端"
```

---

### Task 10: 前端 WikiPage 基础页面

**Files:**
- Create: `frontend/src/pages/WikiPage.tsx`
- Modify: `frontend/src/App.tsx` (添加路由)
- Modify: `frontend/src/pages/HomePage.tsx` (添加入口)

- [ ] **Step 1: 创建 WikiPage.tsx**

```tsx
// frontend/src/pages/WikiPage.tsx
import { useState, useEffect } from 'react'
import { wikiApi } from '@/api/wiki'
import type { WikiIndexEntry, WikiGraphData } from '@/types/wiki'

export function WikiPage() {
  const [pages, setPages] = useState<WikiIndexEntry[]>([])
  const [selectedPage, setSelectedPage] = useState<string | null>(null)
  const [pageContent, setPageContent] = useState<string>('')
  const [graphData, setGraphData] = useState<WikiGraphData | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<'content' | 'graph'>('content')
  const [activeCategory, setActiveCategory] = useState<string>('all')

  // 加载页面列表
  useEffect(() => {
    loadPages()
    loadGraph()
  }, [])

  const loadPages = async () => {
    try {
      setLoading(true)
      const data = await wikiApi.getPages()
      setPages(data)
    } catch (err) {
      console.error('加载页面列表失败:', err)
    } finally {
      setLoading(false)
    }
  }

  const loadGraph = async () => {
    try {
      const data = await wikiApi.getGraph()
      setGraphData(data)
    } catch (err) {
      console.error('加载图谱数据失败:', err)
    }
  }

  // 加载页面内容
  const loadPageContent = async (path: string) => {
    try {
      setSelectedPage(path)
      const content = await wikiApi.getPage(path)
      setPageContent(content)
    } catch (err) {
      console.error('加载页面内容失败:', err)
      setPageContent('加载失败')
    }
  }

  // 上传文档
  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    try {
      const result = await wikiApi.uploadDocument(file)
      console.log('上传成功:', result)
      loadPages()
      loadGraph()
    } catch (err) {
      console.error('上传失败:', err)
    }
  }

  // 按分类筛选
  const filteredPages = activeCategory === 'all'
    ? pages
    : pages.filter(p => p.category === activeCategory)

  // 统计各分类数量
  const categoryCounts = {
    all: pages.length,
    entity: pages.filter(p => p.category === 'entity').length,
    concept: pages.filter(p => p.category === 'concept').length,
    source: pages.filter(p => p.category === 'source').length,
  }

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      {/* 侧边栏 */}
      <div className="w-64 border-r border-gray-200 bg-gray-50 flex flex-col">
        {/* 上传区域 */}
        <div className="p-4 border-b border-gray-200">
          <label className="flex items-center justify-center px-4 py-2 bg-primary-600 text-white rounded-lg cursor-pointer hover:bg-primary-700 transition-colors">
            <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            上传文档
            <input type="file" className="hidden" onChange={handleUpload} accept=".md,.txt,.pdf,.docx" />
          </label>
        </div>

        {/* 分类筛选 */}
        <div className="p-2 border-b border-gray-200">
          <div className="flex flex-wrap gap-1">
            {(['all', 'entity', 'concept', 'source'] as const).map(cat => (
              <button
                key={cat}
                onClick={() => setActiveCategory(cat)}
                className={`px-2 py-1 text-xs rounded ${
                  activeCategory === cat
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                }`}
              >
                {cat === 'all' ? '全部' : cat === 'entity' ? '实体' : cat === 'concept' ? '概念' : '来源'}
                <span className="ml-1">({categoryCounts[cat]})</span>
              </button>
            ))}
          </div>
        </div>

        {/* 页面列表 */}
        <div className="flex-1 overflow-y-auto p-2">
          {loading ? (
            <div className="text-center text-gray-500 py-4">加载中...</div>
          ) : filteredPages.length === 0 ? (
            <div className="text-center text-gray-500 py-4">暂无页面</div>
          ) : (
            <div className="space-y-1">
              {filteredPages.map(page => (
                <button
                  key={page.id}
                  onClick={() => loadPageContent(page.pagePath)}
                  className={`w-full text-left px-3 py-2 rounded text-sm ${
                    selectedPage === page.pagePath
                      ? 'bg-primary-100 text-primary-800'
                      : 'hover:bg-gray-200'
                  }`}
                >
                  <div className="font-medium truncate">{page.title}</div>
                  <div className="text-xs text-gray-500 truncate">{page.summary}</div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 主内容区 */}
      <div className="flex-1 flex flex-col">
        {/* 标签页切换 */}
        <div className="border-b border-gray-200 bg-white">
          <div className="flex">
            <button
              onClick={() => setActiveTab('content')}
              className={`px-6 py-3 text-sm font-medium border-b-2 ${
                activeTab === 'content'
                  ? 'border-primary-600 text-primary-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              页面内容
            </button>
            <button
              onClick={() => setActiveTab('graph')}
              className={`px-6 py-3 text-sm font-medium border-b-2 ${
                activeTab === 'graph'
                  ? 'border-primary-600 text-primary-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              知识图谱
              {graphData && (
                <span className="ml-2 px-2 py-0.5 text-xs bg-gray-200 rounded-full">
                  {graphData.entities.length} 实体
                </span>
              )}
            </button>
          </div>
        </div>

        {/* 内容区域 */}
        <div className="flex-1 overflow-y-auto p-6">
          {activeTab === 'content' ? (
            selectedPage ? (
              <div className="prose max-w-none">
                <pre className="whitespace-pre-wrap font-sans">{pageContent}</pre>
              </div>
            ) : (
              <div className="flex items-center justify-center h-full text-gray-500">
                <div className="text-center">
                  <svg className="w-16 h-16 mx-auto mb-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                  <p>选择左侧页面查看内容</p>
                  <p className="text-sm mt-2">或上传文档开始构建知识图谱</p>
                </div>
              </div>
            )
          ) : (
            <div className="flex items-center justify-center h-full text-gray-500">
              <div className="text-center">
                <svg className="w-16 h-16 mx-auto mb-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
                </svg>
                <p>3D 知识图谱</p>
                <p className="text-sm mt-2">Phase 3c-4 实现</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: 修改 App.tsx 添加路由**

在 `App.tsx` 的 Routes 中添加：

```tsx
<Route path="/wiki" element={<WikiPage />} />
```

并添加 import：

```tsx
import { WikiPage } from './pages/WikiPage'
```

- [ ] **Step 3: 修改 HomePage.tsx 添加入口**

在 `HomePage.tsx` 的 modules 数组中添加：

```typescript
{
  path: '/wiki',
  title: 'Wiki 知识图谱',
  desc: 'LLM 驱动的知识图谱，自动构建实体关系。',
  variation: 4,
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/WikiPage.tsx frontend/src/App.tsx frontend/src/pages/HomePage.tsx
git commit -m "feat(wiki): WikiPage 基础页面 + 路由 + 首页入口"
```

---

## Phase 3c-2: LLM 摄取

### Task 11: WikiLlmService LLM 分析服务

**Files:**
- Create: `backend/src/main/java/com/devknowledge/service/WikiLlmService.java`

- [ ] **Step 1: 创建 WikiLlmService**

```java
package com.devknowledge.service;

import com.devknowledge.service.ai.AiProviderAdapter;
import com.devknowledge.service.ai.AiProviderFactory;
import com.devknowledge.service.ai.AiChunk;
import com.devknowledge.service.ai.AiChunkType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WikiLlmService {

    private final AiProviderFactory aiProviderFactory;
    private final ObjectMapper objectMapper;

    /**
     * 基础分析：生成文档摘要
     */
    public Mono<String> generateSummary(String filename, String content) {
        String prompt = """
                请为以下文档生成一段简洁的摘要（200-300字），提取核心要点。

                文档名称: %s

                文档内容:
                %s

                请直接输出摘要内容，不要添加额外说明。
                """.formatted(filename, content.length() > 3000 ? content.substring(0, 3000) + "..." : content);

        return callLlm(prompt);
    }

    /**
     * 深度分析：提取实体和关系
     */
    public Mono<AnalysisResult> analyzeEntities(String content, String filename) {
        String prompt = """
                分析以下文档，提取其中的关键实体和它们之间的关系。

                文档名称: %s

                文档内容:
                %s

                请以 JSON 格式输出，格式如下:
                {
                  "entities": [
                    {
                      "name": "实体名称",
                      "type": "concept/framework/api/tool",
                      "description": "简要描述（50字以内）"
                    }
                  ],
                  "relations": [
                    {
                      "source": "源实体名称",
                      "target": "目标实体名称",
                      "relation": "uses/extends/contradicts/related_to",
                      "description": "关系描述",
                      "strength": 0.8
                    }
                  ],
                  "summary": "文档整体摘要（200字以内）"
                }

                注意:
                1. 实体名称使用英文小写，用 - 连接（如 react, virtual-dom）
                2. 只提取有意义的核心实体，不要过于细碎
                3. strength 范围 0.0-1.0，表示关系的紧密程度
                4. 只输出 JSON，不要其他内容
                """.formatted(filename, content.length() > 5000 ? content.substring(0, 5000) + "..." : content);

        return callLlm(prompt)
                .flatMap(response -> {
                    try {
                        // 提取 JSON 部分
                        String json = extractJson(response);
                        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});

                        AnalysisResult analysis = new AnalysisResult();
                        analysis.setSummary((String) result.get("summary"));

                        // 解析实体
                        List<Map<String, Object>> entitiesRaw = (List<Map<String, Object>>) result.get("entities");
                        List<EntityInfo> entities = new ArrayList<>();
                        if (entitiesRaw != null) {
                            for (Map<String, Object> e : entitiesRaw) {
                                EntityInfo info = new EntityInfo();
                                info.setName((String) e.get("name"));
                                info.setType((String) e.get("type"));
                                info.setDescription((String) e.get("description"));
                                entities.add(info);
                            }
                        }
                        analysis.setEntities(entities);

                        // 解析关系
                        List<Map<String, Object>> relationsRaw = (List<Map<String, Object>>) result.get("relations");
                        List<RelationInfo> relations = new ArrayList<>();
                        if (relationsRaw != null) {
                            for (Map<String, Object> r : relationsRaw) {
                                RelationInfo info = new RelationInfo();
                                info.setSource((String) r.get("source"));
                                info.setTarget((String) r.get("target"));
                                info.setRelation((String) r.get("relation"));
                                info.setDescription((String) r.get("description"));
                                info.setStrength(r.get("strength") instanceof Number
                                        ? ((Number) r.get("strength")).doubleValue() : 0.5);
                                relations.add(info);
                            }
                        }
                        analysis.setRelations(relations);

                        return Mono.just(analysis);
                    } catch (Exception e) {
                        log.error("解析 LLM 分析结果失败: {}", e.getMessage());
                        // 返回空结果
                        AnalysisResult fallback = new AnalysisResult();
                        fallback.setSummary("分析失败，请重试");
                        fallback.setEntities(List.of());
                        fallback.setRelations(List.of());
                        return Mono.just(fallback);
                    }
                });
    }

    /**
     * 生成 wiki 页面内容
     */
    public Mono<String> generateWikiPage(String entityName, String entityType, String description, String sourceContent) {
        String prompt = """
                为以下实体生成一个 wiki 页面（markdown 格式）。

                实体名称: %s
                实体类型: %s
                描述: %s

                相关源内容:
                %s

                请生成一个结构化的 wiki 页面，包含:
                1. YAML frontmatter（type, category, tags, created）
                2. 标题和简介
                3. 核心概念或要点
                4. 使用 [[实体名]] 格式标注相关实体链接
                5. 来源引用

                直接输出 markdown 内容。
                """.formatted(entityName, entityType, description,
                    sourceContent.length() > 2000 ? sourceContent.substring(0, 2000) + "..." : sourceContent);

        return callLlm(prompt);
    }

    private Mono<String> callLlm(String prompt) {
        try {
            AiProviderAdapter adapter = aiProviderFactory.getAdapter();
            Flux<AiChunk> stream = adapter.chatStream(
                    "你是知识图谱分析专家，擅长从文档中提取实体和关系。",
                    prompt);

            return stream
                    .filter(chunk -> chunk.getType() == AiChunkType.TEXT)
                    .map(AiChunk::getContent)
                    .collectList()
                    .map(parts -> String.join("", parts));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("LLM 调用失败: " + e.getMessage()));
        }
    }

    private String extractJson(String text) {
        // 尝试提取 JSON 块
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    // 内部数据类
    @lombok.Data
    public static class AnalysisResult {
        private String summary;
        private List<EntityInfo> entities;
        private List<RelationInfo> relations;
    }

    @lombok.Data
    public static class EntityInfo {
        private String name;
        private String type;
        private String description;
    }

    @lombok.Data
    public static class RelationInfo {
        private String source;
        private String target;
        private String relation;
        private String description;
        private Double strength;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/WikiLlmService.java
git commit -m "feat(wiki): WikiLlmService - LLM 实体提取和页面生成"
```

---

### Task 12: 增强 WikiIngestService 集成 LLM

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/WikiIngestService.java`

- [ ] **Step 1: 注入 WikiLlmService**

在 WikiIngestService 中添加依赖：

```java
private final WikiLlmService wikiLlmService;
```

- [ ] **Step 2: 修改 ingestDocument 方法集成 LLM 分析**

```java
/**
 * 摄取文档：存储原始文件 + LLM 分析 + 生成 wiki 页面
 */
public Mono<WikiDocument> ingestDocument(UUID userId, String filename, byte[] fileBytes) {
    return Mono.fromCallable(() -> {
        // 1. 初始化 vault
        wikiFileService.initUserVault(userId).block();

        // 2. 解析文件内容
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        String fileType = extractFileType(filename);

        // 3. 存储文档记录
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
    .flatMap(doc -> {
        // 4. LLM 分析
        return wikiLlmService.analyzeEntities(doc.getContent(), doc.getFilename())
                .flatMap(analysis -> {
                    // 5. 生成来源摘要页面
                    String summaryPath = "sources/" + sanitizeFilename(filename) + "-summary.md";
                    String summaryContent = generateSummaryPageWithAnalysis(
                            filename, analysis.getSummary(), doc.getId());
                    wikiFileService.writePage(userId, summaryPath, summaryContent).block();

                    // 6. 创建实体页面和索引
                    for (WikiLlmService.EntityInfo entity : analysis.getEntities()) {
                        String entityPath = "entities/" + entity.getName() + ".md";
                        String entityPage = generateEntityPage(entity, doc.getId());
                        wikiFileService.writePage(userId, entityPath, entityPage).block();

                        // 存储实体记录
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

                        // 添加索引
                        WikiIndex index = new WikiIndex();
                        index.setId(UUID.randomUUID());
                        index.setUserId(userId);
                        index.setPagePath(entityPath);
                        index.setTitle(entity.getName());
                        index.setCategory("entity");
                        index.setTags(new String[]{entity.getType()});
                        index.setSummary(entity.getDescription());
                        index.setDocIds(new String[]{doc.getId().toString()});
                        index.setUpdatedAt(Instant.now());
                        wikiIndexMapper.insert(index);
                    }

                    // 7. 存储关系
                    Map<String, UUID> entityNameToId = new HashMap<>();
                    for (WikiLlmService.EntityInfo entity : analysis.getEntities()) {
                        entityNameToId.put(entity.getName(), findEntityId(userId, entity.getName()));
                    }

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

                    // 8. 更新文档状态
                    doc.setStatus("ready");
                    wikiDocumentMapper.updateById(doc);

                    // 9. 写入 log
                    appendToLog(userId, "ingest", filename,
                            "文档类型: " + doc.getFileType() + "\n- 提取实体: " + analysis.getEntities().size() + " 个\n- 建立关系: " + analysis.getRelations().size() + " 条");

                    log.info("文档摄取完成: {} -> {} 实体, {} 关系",
                            filename, analysis.getEntities().size(), analysis.getRelations().size());
                    return Mono.just(doc);
                })
                .onErrorResume(e -> {
                    log.error("文档分析失败: {}", e.getMessage());
                    doc.setStatus("error");
                    doc.setErrorMsg(e.getMessage());
                    wikiDocumentMapper.updateById(doc);
                    return Mono.just(doc);
                });
    });
}

private String generateSummaryPageWithAnalysis(String filename, String summary, UUID docId) {
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

private UUID findEntityId(UUID userId, String entityName) {
    return wikiEntityMapper.selectOne(
            new LambdaQueryWrapper<WikiEntity>()
                    .eq(WikiEntity::getUserId, userId)
                    .eq(WikiEntity::getName, entityName))
            .getId();
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/WikiIngestService.java
git commit -m "feat(wiki): WikiIngestService 集成 LLM 分析 - 自动生成实体和关系"
```

---

### Task 13: 前端 WikiUpload 组件

**Files:**
- Create: `frontend/src/components/wiki/WikiUpload.tsx`
- Modify: `frontend/src/pages/WikiPage.tsx` (使用组件)

- [ ] **Step 1: 创建 WikiUpload 组件**

```tsx
// frontend/src/components/wiki/WikiUpload.tsx
import { useState, useRef } from 'react'
import { wikiApi } from '@/api/wiki'
import type { WikiUploadResponse } from '@/types/wiki'

interface WikiUploadProps {
  onUploadSuccess: (result: WikiUploadResponse) => void
}

export function WikiUpload({ onUploadSuccess }: WikiUploadProps) {
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    setUploading(true)
    setError(null)

    try {
      const result = await wikiApi.uploadDocument(file)
      onUploadSuccess(result)
      // 清空 input
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="space-y-2">
      <label className={`flex items-center justify-center px-4 py-2 rounded-lg cursor-pointer transition-colors ${
        uploading
          ? 'bg-gray-400 cursor-not-allowed'
          : 'bg-primary-600 hover:bg-primary-700 text-white'
      }`}>
        {uploading ? (
          <>
            <svg className="animate-spin w-5 h-5 mr-2" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            上传中...
          </>
        ) : (
          <>
            <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            上传文档
          </>
        )}
        <input
          ref={fileInputRef}
          type="file"
          className="hidden"
          onChange={handleFileSelect}
          accept=".md,.txt,.pdf,.docx"
          disabled={uploading}
        />
      </label>

      {error && (
        <div className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">
          {error}
        </div>
      )}

      <p className="text-xs text-gray-500 text-center">
        支持 .md, .txt, .pdf, .docx 格式
      </p>
    </div>
  )
}
```

- [ ] **Step 2: 更新 WikiPage 使用 WikiUpload 组件**

修改 WikiPage.tsx 的侧边栏上传区域：

```tsx
import { WikiUpload } from '@/components/wiki/WikiUpload'

// 在侧边栏上传区域替换为:
<div className="p-4 border-b border-gray-200">
  <WikiUpload onUploadSuccess={(result) => {
    console.log('上传成功:', result)
    loadPages()
    loadGraph()
  }} />
</div>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wiki/WikiUpload.tsx frontend/src/pages/WikiPage.tsx
git commit -m "feat(wiki): WikiUpload 组件 - 文档上传和进度显示"
```

---

## Phase 3c-3: 知识图谱

### Task 14: 深度分析 API 端点

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/controller/WikiController.java`
- Modify: `backend/src/main/java/com/devknowledge/service/WikiIngestService.java`

- [ ] **Step 1: 在 WikiController 添加深度分析端点**

```java
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
```

- [ ] **Step 2: 在 WikiIngestService 添加 deepAnalyze 方法**

```java
/**
 * 深度分析：提取实体、关系、矛盾标注
 */
public Mono<WikiDocument> deepAnalyze(UUID userId, UUID docId) {
    return Mono.fromCallable(() -> {
        WikiDocument doc = wikiDocumentMapper.selectById(docId);
        if (doc == null || !doc.getUserId().equals(userId)) {
            throw new RuntimeException("文档不存在或无权限");
        }
        return doc;
    })
    .flatMap(doc -> wikiLlmService.analyzeEntities(doc.getContent(), doc.getFilename())
            .flatMap(analysis -> {
                // 清除旧的实体和关系
                var oldEntities = wikiEntityMapper.selectList(
                        new LambdaQueryWrapper<WikiEntity>()
                                .eq(WikiEntity::getDocId, docId));
                for (WikiEntity old : oldEntities) {
                    wikiRelationMapper.delete(
                            new LambdaQueryWrapper<WikiRelation>()
                                    .eq(WikiRelation::getSourceId, old.getId())
                                    .or()
                                    .eq(WikiRelation::getTargetId, old.getId()));
                }
                wikiEntityMapper.delete(
                        new LambdaQueryWrapper<WikiEntity>().eq(WikiEntity::getDocId, docId));

                // 创建新实体
                Map<String, UUID> entityNameToId = new HashMap<>();
                for (WikiLlmService.EntityInfo entity : analysis.getEntities()) {
                    String entityPath = "entities/" + entity.getName() + ".md";
                    String entityPage = generateEntityPage(entity, docId);
                    wikiFileService.writePage(userId, entityPath, entityPage).block();

                    WikiEntity wikiEntity = new WikiEntity();
                    wikiEntity.setId(UUID.randomUUID());
                    wikiEntity.setUserId(userId);
                    wikiEntity.setName(entity.getName());
                    wikiEntity.setType(entity.getType());
                    wikiEntity.setDescription(entity.getDescription());
                    wikiEntity.setPagePath(entityPath);
                    wikiEntity.setDocId(docId);
                    wikiEntity.setCreatedAt(Instant.now());
                    wikiEntity.setUpdatedAt(Instant.now());
                    wikiEntityMapper.insert(wikiEntity);
                    entityNameToId.put(entity.getName(), wikiEntity.getId());

                    // 更新索引
                    WikiIndex existingIndex = wikiIndexMapper.selectOne(
                            new LambdaQueryWrapper<WikiIndex>()
                                    .eq(WikiIndex::getUserId, userId)
                                    .eq(WikiIndex::getPagePath, entityPath));
                    if (existingIndex != null) {
                        existingIndex.setSummary(entity.getDescription());
                        existingIndex.setUpdatedAt(Instant.now());
                        wikiIndexMapper.updateById(existingIndex);
                    } else {
                        WikiIndex index = new WikiIndex();
                        index.setId(UUID.randomUUID());
                        index.setUserId(userId);
                        index.setPagePath(entityPath);
                        index.setTitle(entity.getName());
                        index.setCategory("entity");
                        index.setTags(new String[]{entity.getType()});
                        index.setSummary(entity.getDescription());
                        index.setDocIds(new String[]{docId.toString()});
                        index.setUpdatedAt(Instant.now());
                        wikiIndexMapper.insert(index);
                    }
                }

                // 创建新关系
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

                // 写入 log
                appendToLog(userId, "deep-analysis", doc.getFilename(),
                        "提取实体: " + analysis.getEntities().size() + " 个\n- 建立关系: " + analysis.getRelations().size() + " 条");

                log.info("深度分析完成: {} -> {} 实体, {} 关系",
                        doc.getFilename(), analysis.getEntities().size(), analysis.getRelations().size());
                return Mono.just(doc);
            })
    );
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/controller/WikiController.java backend/src/main/java/com/devknowledge/service/WikiIngestService.java
git commit -m "feat(wiki): 深度分析 API - 实体提取和关系识别"
```

---

### Task 15: 前端深度分析按钮

**Files:**
- Modify: `frontend/src/pages/WikiPage.tsx`
- Modify: `frontend/src/api/wiki.ts`

- [ ] **Step 1: 在 wiki.ts 添加分析 API**

```typescript
/**
 * 触发深度分析
 */
analyzeDocument: (docId: string) =>
  api.post<{ docId: string; status: string; message: string }>(`/wiki/analyze/${docId}`),
```

- [ ] **Step 2: 在 WikiPage 添加分析按钮**

在页面内容区域添加分析按钮：

```tsx
const [analyzing, setAnalyzing] = useState(false)

const handleAnalyze = async (docId: string) => {
  try {
    setAnalyzing(true)
    await wikiApi.analyzeDocument(docId)
    loadPages()
    loadGraph()
  } catch (err) {
    console.error('分析失败:', err)
  } finally {
    setAnalyzing(false)
  }
}

// 在内容区域添加按钮
{selectedPage && selectedPage.startsWith('sources/') && (
  <button
    onClick={() => {
      const page = pages.find(p => p.pagePath === selectedPage)
      if (page?.docIds?.[0]) {
        handleAnalyze(page.docIds[0])
      }
    }}
    disabled={analyzing}
    className="mb-4 px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:bg-gray-400"
  >
    {analyzing ? '分析中...' : '深度分析'}
  </button>
)}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/WikiPage.tsx frontend/src/api/wiki.ts
git commit -m "feat(wiki): 前端深度分析按钮"
```

---

## Phase 3c-4: 3D 可视化

### Task 16: WikiGraph3D 组件

**Files:**
- Create: `frontend/src/components/wiki/WikiGraph3D.tsx`
- Create: `frontend/src/components/wiki/GraphNode.tsx`

- [ ] **Step 1: 创建 GraphNode 组件**

```tsx
// frontend/src/components/wiki/GraphNode.tsx
import { useRef, useState } from 'react'
import { useFrame } from '@react-three/fiber'
import { Html } from '@react-three/drei'
import * as THREE from 'three'

interface GraphNodeProps {
  position: [number, number, number]
  name: string
  type: string
  description?: string
  size?: number
  onClick?: () => void
}

const NODE_COLORS: Record<string, string> = {
  framework: '#3b82f6', // 蓝色
  concept: '#22c55e',   // 绿色
  api: '#f97316',       // 橙色
  tool: '#a855f7',      // 紫色
  default: '#6b7280',   // 灰色
}

export function GraphNode({ position, name, type, description, size = 0.5, onClick }: GraphNodeProps) {
  const meshRef = useRef<THREE.Mesh>(null)
  const [hovered, setHovered] = useState(false)
  const color = NODE_COLORS[type] || NODE_COLORS.default

  // 悬浮动画
  useFrame((state) => {
    if (meshRef.current) {
      meshRef.current.rotation.y += 0.01
      if (hovered) {
        meshRef.current.scale.lerp(new THREE.Vector3(1.3, 1.3, 1.3), 0.1)
      } else {
        meshRef.current.scale.lerp(new THREE.Vector3(1, 1, 1), 0.1)
      }
    }
  })

  return (
    <group position={position}>
      <mesh
        ref={meshRef}
        onPointerOver={(e) => {
          e.stopPropagation()
          setHovered(true)
        }}
        onPointerOut={() => setHovered(false)}
        onClick={(e) => {
          e.stopPropagation()
          onClick?.()
        }}
      >
        <icosahedronGeometry args={[size, 1]} />
        <meshStandardMaterial
          color={color}
          emissive={hovered ? color : '#000000'}
          emissiveIntensity={hovered ? 0.5 : 0}
          roughness={0.3}
          metalness={0.7}
        />
      </mesh>

      {/* 标签 */}
      {hovered && (
        <Html distanceFactor={10} position={[0, size + 0.5, 0]}>
          <div className="bg-gray-900 text-white px-3 py-2 rounded-lg shadow-lg whitespace-nowrap pointer-events-none">
            <div className="font-bold text-sm">{name}</div>
            {description && (
              <div className="text-xs text-gray-300 max-w-48 truncate">{description}</div>
            )}
            <div className="text-xs mt-1" style={{ color }}>
              {type}
            </div>
          </div>
        </Html>
      )}
    </group>
  )
}
```

- [ ] **Step 2: 创建 WikiGraph3D 组件**

```tsx
// frontend/src/components/wiki/WikiGraph3D.tsx
import { useMemo, useCallback } from 'react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, Float } from '@react-three/drei'
import { GraphNode } from './GraphNode'
import type { WikiGraphData } from '@/types/wiki'

interface WikiGraph3DProps {
  data: WikiGraphData
  onNodeClick?: (entityId: string, pagePath?: string) => void
}

// 简单的力导向布局算法
function calculateLayout(data: WikiGraphData): Map<string, [number, number, number]> {
  const positions = new Map<string, [number, number, number]>()
  const entityCount = data.entities.length

  // 圆形布局
  data.entities.forEach((entity, index) => {
    const angle = (index / entityCount) * Math.PI * 2
    const radius = Math.max(5, entityCount * 0.5)
    const x = Math.cos(angle) * radius
    const z = Math.sin(angle) * radius
    const y = (Math.random() - 0.5) * 2 // 随机高度
    positions.set(entity.id, [x, y, z])
  })

  return positions
}

export function WikiGraph3D({ data, onNodeClick }: WikiGraph3DProps) {
  const positions = useMemo(() => calculateLayout(data), [data])

  // 计算节点大小（根据关系数量）
  const nodeSizes = useMemo(() => {
    const sizes = new Map<string, number>()
    const relationCounts = new Map<string, number>()

    data.relations.forEach(rel => {
      relationCounts.set(rel.sourceId, (relationCounts.get(rel.sourceId) || 0) + 1)
      relationCounts.set(rel.targetId, (relationCounts.get(rel.targetId) || 0) + 1)
    })

    data.entities.forEach(entity => {
      const count = relationCounts.get(entity.id) || 0
      sizes.set(entity.id, 0.3 + count * 0.1)
    })

    return sizes
  }, [data])

  const handleNodeClick = useCallback((entityId: string) => {
    const entity = data.entities.find(e => e.id === entityId)
    if (entity) {
      onNodeClick?.(entityId, entity.pagePath)
    }
  }, [data, onNodeClick])

  return (
    <div className="w-full h-full">
      <Canvas camera={{ position: [0, 5, 15], fov: 60 }}>
        <ambientLight intensity={0.5} />
        <pointLight position={[10, 10, 10]} intensity={1} />
        <pointLight position={[-10, -10, -10]} intensity={0.5} />

        {/* 渲染边 */}
        {data.relations.map((rel, index) => {
          const sourcePos = positions.get(rel.sourceId)
          const targetPos = positions.get(rel.targetId)
          if (!sourcePos || !targetPos) return null

          const points = [
            new Float32Array(sourcePos),
            new Float32Array(targetPos),
          ]

          return (
            <line key={index}>
              <bufferGeometry>
                <bufferAttribute
                  attach="attributes-position"
                  count={2}
                  array={new Float32Array([...sourcePos, ...targetPos])}
                  itemSize={3}
                />
              </bufferGeometry>
              <lineBasicMaterial
                color="#ffffff"
                opacity={rel.strength * 0.5}
                transparent
              />
            </line>
          )
        })}

        {/* 渲染节点 */}
        {data.entities.map(entity => {
          const pos = positions.get(entity.id)
          if (!pos) return null

          return (
            <GraphNode
              key={entity.id}
              position={pos}
              name={entity.name}
              type={entity.type}
              description={entity.description}
              size={nodeSizes.get(entity.id)}
              onClick={() => handleNodeClick(entity.id)}
            />
          )
        })}

        <OrbitControls enableDamping dampingFactor={0.05} />
      </Canvas>
    </div>
  )
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/wiki/WikiGraph3D.tsx frontend/src/components/wiki/GraphNode.tsx
git commit -m "feat(wiki): WikiGraph3D 组件 - 3D 粒子图谱可视化"
```

---

### Task 17: 集成 3D 图谱到 WikiPage

**Files:**
- Modify: `frontend/src/pages/WikiPage.tsx`

- [ ] **Step 1: 导入 WikiGraph3D**

```tsx
import { WikiGraph3D } from '@/components/wiki/WikiGraph3D'
```

- [ ] **Step 2: 替换图谱占位符**

```tsx
// 替换图谱标签页内容
{activeTab === 'graph' ? (
  graphData && graphData.entities.length > 0 ? (
    <WikiGraph3D
      data={graphData}
      onNodeClick={(entityId, pagePath) => {
        if (pagePath) {
          loadPageContent(pagePath)
          setActiveTab('content')
        }
      }}
    />
  ) : (
    <div className="flex items-center justify-center h-full text-gray-500">
      <div className="text-center">
        <svg className="w-16 h-16 mx-auto mb-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
        </svg>
        <p>暂无图谱数据</p>
        <p className="text-sm mt-2">上传文档并进行深度分析</p>
      </div>
    </div>
  )
) : null}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/WikiPage.tsx
git commit -m "feat(wiki): 集成 3D 图谱到 WikiPage"
```

---

## Phase 3c-5: 高级功能

### Task 18: Obsidian Vault 目录上传

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/controller/WikiController.java`
- Modify: `backend/src/main/java/com/devknowledge/service/WikiIngestService.java`
- Modify: `frontend/src/api/wiki.ts`
- Modify: `frontend/src/components/wiki/WikiUpload.tsx`

- [ ] **Step 1: 后端添加 vault 上传端点**

```java
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
            .flatMap(fileData -> {
                String filename = (String) fileData[0];
                byte[] bytes = (byte[]) fileData[1];
                // 只处理 markdown 文件
                if (filename.endsWith(".md")) {
                    return wikiIngestService.ingestDocument(userId, filename, bytes)
                            .map(doc -> {
                                WikiUploadResponse resp = new WikiUploadResponse();
                                resp.setDocId(doc.getId());
                                resp.setFilename(doc.getFilename());
                                resp.setStatus(doc.getStatus());
                                return resp;
                            });
                }
                return Mono.empty();
            })
            .collectList()
            .map(ResponseEntity::ok);
}
```

- [ ] **Step 2: 前端添加 vault 上传方法**

```typescript
/**
 * 上传 Obsidian vault 目录
 */
uploadVault: (files: File[]) => {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  const token = localStorage.getItem('accessToken')
  return fetch('/api/wiki/upload-vault', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  }).then(res => {
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json() as Promise<WikiUploadResponse[]>
  })
},
```

- [ ] **Step 3: 更新 WikiUpload 组件支持目录上传**

```tsx
interface WikiUploadProps {
  onUploadSuccess: (result: WikiUploadResponse) => void
  onVaultUploadSuccess?: (results: WikiUploadResponse[]) => void
}

// 添加目录上传按钮
<div className="flex space-x-2">
  <label className={`flex-1 flex items-center justify-center px-4 py-2 rounded-lg cursor-pointer transition-colors ${
    uploading
      ? 'bg-gray-400 cursor-not-allowed'
      : 'bg-primary-600 hover:bg-primary-700 text-white'
  }`}>
    {/* 单文件上传 */}
    <input
      type="file"
      className="hidden"
      onChange={handleFileSelect}
      accept=".md,.txt,.pdf,.docx"
      disabled={uploading}
    />
    上传文档
  </label>

  <label className={`flex-1 flex items-center justify-center px-4 py-2 rounded-lg cursor-pointer transition-colors ${
    uploading
      ? 'bg-gray-400 cursor-not-allowed'
      : 'bg-purple-600 hover:bg-purple-700 text-white'
  }`}>
    {/* 目录上传 */}
    <input
      type="file"
      className="hidden"
      onChange={handleVaultSelect}
      // @ts-ignore
      webkitdirectory=""
      multiple
      disabled={uploading}
    />
    上传 Vault
  </label>
</div>

const handleVaultSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
  const files = Array.from(e.target.files || [])
  if (files.length === 0) return

  setUploading(true)
  setError(null)

  try {
    const mdFiles = files.filter(f => f.name.endsWith('.md'))
    const results = await wikiApi.uploadVault(mdFiles)
    results.forEach(r => onUploadSuccess(r))
    onVaultUploadSuccess?.(results)
  } catch (err) {
    setError(err instanceof Error ? err.message : '上传失败')
  } finally {
    setUploading(false)
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devknowledge/controller/WikiController.java frontend/src/api/wiki.ts frontend/src/components/wiki/WikiUpload.tsx
git commit -m "feat(wiki): Obsidian vault 目录上传支持"
```

---

### Task 19: Demo 页面 Wiki 检索源

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/WikiRetrievalService.java` (新建)
- Modify: `backend/src/main/java/com/devknowledge/service/DemoService.java`
- Modify: `frontend/src/pages/DemoPage.tsx`

- [ ] **Step 1: 创建 WikiRetrievalService**

```java
package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.mapper.WikiIndexMapper;
import com.devknowledge.model.WikiIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WikiRetrievalService {

    private final WikiIndexMapper wikiIndexMapper;
    private final WikiFileService wikiFileService;

    /**
     * 根据查询检索相关 wiki 页面
     */
    public Mono<String> retrieveContext(UUID userId, String query) {
        return Mono.fromCallable(() -> {
            // 1. 获取所有索引
            List<WikiIndex> allIndex = wikiIndexMapper.selectList(
                    new LambdaQueryWrapper<WikiIndex>()
                            .eq(WikiIndex::getUserId, userId));

            // 2. 简单关键词匹配（后续可优化为语义匹配）
            List<WikiIndex> relevant = allIndex.stream()
                    .filter(idx -> {
                        String searchText = (idx.getTitle() + " " + idx.getSummary()).toLowerCase();
                        String queryLower = query.toLowerCase();
                        // 简单包含匹配
                        for (String keyword : queryLower.split("\\s+")) {
                            if (searchText.contains(keyword)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .limit(5) // 最多 5 个页面
                    .collect(Collectors.toList());

            if (relevant.isEmpty()) {
                // 如果没有匹配，返回前 3 个页面
                relevant = allIndex.stream().limit(3).collect(Collectors.toList());
            }

            // 3. 读取页面内容
            StringBuilder context = new StringBuilder();
            context.append("以下是相关的 Wiki 知识库内容:\n\n");

            for (WikiIndex idx : relevant) {
                String content = wikiFileService.readPage(userId, idx.getPagePath()).block();
                if (content != null) {
                    context.append("## ").append(idx.getTitle()).append("\n");
                    context.append(content).append("\n\n");
                }
            }

            return context.toString();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
```

- [ ] **Step 2: 修改 DemoService 集成 Wiki 检索**

在 DemoService 中添加 WikiRetrievalService 依赖，并修改 generateDemo 方法：

```java
// 添加依赖注入
private final WikiRetrievalService wikiRetrievalService;

// 在 generateDemo 方法中添加 wiki 检索逻辑
if ("wiki".equals(retrievalSource)) {
    String wikiContext = wikiRetrievalService.retrieveContext(userId, prompt).block();
    if (wikiContext != null && !wikiContext.isEmpty()) {
        systemPrompt += "\n\n" + wikiContext;
    }
}
```

- [ ] **Step 3: 前端 DemoPage 添加检索源切换**

```tsx
// 在 DemoPage 的输入区域添加
const [retrievalSource, setRetrievalSource] = useState<'rag' | 'wiki' | 'none'>('none')

<div className="flex items-center space-x-4 mb-4">
  <span className="text-sm text-gray-600">检索源:</span>
  {(['rag', 'wiki', 'none'] as const).map(source => (
    <label key={source} className="flex items-center">
      <input
        type="radio"
        value={source}
        checked={retrievalSource === source}
        onChange={(e) => setRetrievalSource(e.target.value as typeof retrievalSource)}
        className="mr-1"
      />
      <span className="text-sm">{source === 'rag' ? 'RAG' : source === 'wiki' ? 'Wiki' : '无'}</span>
    </label>
  ))}
</div>

// 修改生成请求，传递 retrievalSource
const response = await fetch('/api/demos/generate', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  },
  body: JSON.stringify({
    prompt,
    retrievalSource,
    // ... 其他参数
  }),
})
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/WikiRetrievalService.java backend/src/main/java/com/devknowledge/service/DemoService.java frontend/src/pages/DemoPage.tsx
git commit -m "feat(wiki): Demo 页面集成 Wiki 检索源"
```

---

### Task 20: Lint 健康检查

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/WikiLlmService.java`
- Modify: `backend/src/main/java/com/devknowledge/controller/WikiController.java`

- [ ] **Step 1: 在 WikiLlmService 添加 lint 方法**

```java
/**
 * Lint 健康检查：检测矛盾、孤立页面、缺失链接
 */
public Mono<LintResult> lintWiki(UUID userId, List<WikiIndex> pages, List<WikiEntity> entities) {
    String pagesSummary = pages.stream()
            .map(p -> "- " + p.getTitle() + " (" + p.getCategory() + ")")
            .limit(50)
            .collect(Collectors.joining("\n"));

    String entitiesSummary = entities.stream()
            .map(e -> "- " + e.getName() + " (" + e.getType() + ")")
            .limit(50)
            .collect(Collectors.joining("\n"));

    String prompt = """
            对以下 Wiki 知识库进行健康检查。

            页面列表:
            %s

            实体列表:
            %s

            请检测以下问题并以 JSON 格式输出:
            {
              "contradictions": ["矛盾描述1", "矛盾描述2"],
              "orphanPages": ["孤立页面1", "孤立页面2"],
              "missingLinks": [
                {"from": "页面A", "to": "页面B", "reason": "应该建立链接的原因"}
              ],
              "suggestions": ["建议1", "建议2"]
            }

            只输出 JSON，不要其他内容。
            """.formatted(pagesSummary, entitiesSummary);

    return callLlm(prompt)
            .flatMap(response -> {
                try {
                    String json = extractJson(response);
                    Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
                    LintResult lint = new LintResult();
                    lint.setContradictions((List<String>) result.getOrDefault("contradictions", List.of()));
                    lint.setOrphanPages((List<String>) result.getOrDefault("orphanPages", List.of()));
                    lint.setSuggestions((List<String>) result.getOrDefault("suggestions", List.of()));
                    return Mono.just(lint);
                } catch (Exception e) {
                    LintResult fallback = new LintResult();
                    fallback.setSuggestions(List.of("分析失败，请重试"));
                    return Mono.just(fallback);
                }
            });
}

@lombok.Data
public static class LintResult {
    private List<String> contradictions;
    private List<String> orphanPages;
    private List<Map<String, String>> missingLinks;
    private List<String> suggestions;
}
```

- [ ] **Step 2: 添加 lint API 端点**

```java
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
    ).flatMap(tuple -> wikiLlmService.lintWiki(userId, tuple.getT1(), tuple.getT2()))
            .map(ResponseEntity::ok);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/WikiLlmService.java backend/src/main/java/com/devknowledge/controller/WikiController.java
git commit -m "feat(wiki): Lint 健康检查 - 矛盾检测和孤立页面识别"
```

---

## 最终验证

### Task 21: 编译和测试

- [ ] **Step 1: 后端编译验证**

```bash
cd backend && mvn compile
```

预期：BUILD SUCCESS

- [ ] **Step 2: 前端编译验证**

```bash
cd frontend && npm run build
```

预期：构建成功，无类型错误

- [ ] **Step 3: 功能测试清单**

1. 启动后端和前端
2. 登录系统
3. 首页点击 Wiki 入口
4. 上传一个 markdown 文档
5. 验证文档出现在侧边栏
6. 点击页面查看内容
7. 切换到图谱标签页查看 3D 可视化
8. 点击"深度分析"按钮
9. 验证实体和关系被提取
10. Demo 页面切换 Wiki 检索源
11. 生成 Demo 验证 Wiki 上下文注入

- [ ] **Step 4: Final Commit**

```bash
git add .
git commit -m "feat(wiki): Phase 3c 完成 - Wiki 知识图谱完整功能"
```
