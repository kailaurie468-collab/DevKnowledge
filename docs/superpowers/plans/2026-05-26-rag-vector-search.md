# Phase 3b: RAG 向量检索 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为知识库引入 pgvector 向量检索，实现语义级 RAG（混合式：预检索注入 + 工具二次搜索），并新增独立的 Embedding AI 配置页面。

**Architecture:** Embedding 不走现有的 AiProviderAdapter 层，由独立的 `EmbeddingService` 直接调用 OpenAI `/v1/embeddings` 端点。用户在 "Embedding AI" 设置页配置 API 凭证（Key + URL），模型和维度在创建知识库时锁定。文档上传后异步切分+向量化，Demo 生成时自动预检索注入 prompt。

**Tech Stack:** pgvector (PostgreSQL 扩展)、Spring Boot 3.3 WebFlux、MyBatis Plus 3.5.7、WebClient、React 19、TypeScript、Zustand

**设计文档:** `docs/superpowers/specs/2026-05-26-rag-vector-search-design.md`

---

## File Structure

### 新增文件（后端）

| 文件 | 职责 |
|------|------|
| `backend/src/main/resources/db/migration/V8__rag_vector_search.sql` | pgvector 扩展 + kb_chunks + user_embedding_configs + embedding_usage 表 |
| `backend/.../model/UserEmbeddingConfig.java` | Embedding 配置实体 |
| `backend/.../model/KbChunk.java` | 文档切片实体 |
| `backend/.../model/EmbeddingUsage.java` | Token 消耗实体 |
| `backend/.../mapper/UserEmbeddingConfigMapper.java` | Embedding 配置 Mapper |
| `backend/.../mapper/KbChunkMapper.java` | 切片 Mapper（含向量检索 SQL） |
| `backend/.../mapper/EmbeddingUsageMapper.java` | Token 消耗 Mapper |
| `backend/.../dto/EmbeddingConfigRequest.java` | Embedding 配置请求 DTO |
| `backend/.../dto/EmbeddingConfigResponse.java` | Embedding 配置响应 DTO |
| `backend/.../dto/KbChunkSearchResult.java` | 向量检索结果 DTO |
| `backend/.../service/EmbeddingService.java` | Embedding API 调用（OpenAI /v1/embeddings） |
| `backend/.../service/EmbeddingConfigService.java` | Embedding 配置 CRUD + 测试 + Token 统计 |
| `backend/.../service/EmbeddingUsageService.java` | Token 消耗记录 |
| `backend/.../controller/EmbeddingConfigController.java` | Embedding 配置 REST API |

### 修改文件（后端）

| 文件 | 变更 |
|------|------|
| `backend/pom.xml` | 新增 `pgvector` JDBC 依赖（或自定义 TypeHandler） |
| `backend/.../model/KnowledgeBase.java` | 新增 `embeddingModel`、`embeddingDimensions` 字段 |
| `backend/.../dto/KbCreateRequest.java` | 新增 `embeddingModel`、`embeddingDimensions` 字段 |
| `backend/.../service/KbService.java` | 新增 `chunkAndEmbed`、`searchKbVector`、`splitIntoChunks` |
| `backend/.../service/DemoService.java` | `generateDemo` 中新增预检索注入 |
| `backend/.../service/ai/DemoToolProvider.java` | `search_kb` handler 改用向量检索 |
| `backend/.../controller/KbController.java` | `searchKb` 返回类型改为 `KbChunkSearchResult` |
| `backend/.../config/SecurityConfig.java` | 放行路径无需变更（`/api/user/**` 已 authenticated） |

### 新增文件（前端）

| 文件 | 职责 |
|------|------|
| `frontend/src/pages/settings/EmbeddingSettings.tsx` | Embedding AI 配置页面 |
| `frontend/src/api/embedding.ts` | Embedding 配置 API 客户端 |

### 修改文件（前端）

| 文件 | 变更 |
|------|------|
| `frontend/src/types/api.ts` | 新增 EmbeddingConfig、EmbeddingConfigRequest、KbDocument.chunkCount、GenerateDemoRequest.topK |
| `frontend/src/pages/SettingsPage.tsx` | 顶部 Tab → 侧边栏导航（3 个子页面） |
| `frontend/src/pages/KbPage.tsx` | 创建知识库时新增模型/维度选择；文档列表显示 chunk 数量 |
| `frontend/src/pages/DemoPage.tsx` | 新增 Top-K 滑块 |
| `frontend/src/api/kb.ts` | `createKb` 参数新增 `embeddingModel`、`embeddingDimensions` |
| `frontend/src/api/settings.ts` | 新增 embedding 相关 API 方法 |

---

## Task 1: V8 Flyway 迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__rag_vector_search.sql`

- [ ] **Step 1: 创建 V8 迁移文件**

```sql
-- V8__rag_vector_search.sql

-- 1. 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 用户 Embedding 配置表
CREATE TABLE user_embedding_configs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100),
    api_key TEXT NOT NULL,
    base_url VARCHAR(500) DEFAULT 'https://api.openai.com/v1',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_user_embedding_configs_active
    ON user_embedding_configs(user_id) WHERE is_active = true;

-- 3. Embedding Token 消耗表
CREATE TABLE embedding_usage (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    config_id UUID NOT NULL REFERENCES user_embedding_configs(id),
    prompt_tokens INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_embedding_usage_user_date ON embedding_usage(user_id, created_at);

-- 4. 文档切片表（向量存储）
CREATE TABLE kb_chunks (
    id UUID PRIMARY KEY,
    kb_id UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    doc_id UUID NOT NULL REFERENCES kb_documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_kb_chunks_kb_id ON kb_chunks(kb_id);
CREATE INDEX idx_kb_chunks_doc_id ON kb_chunks(doc_id);
CREATE INDEX idx_kb_chunks_embedding ON kb_chunks
    USING hnsw (embedding vector_cosine_ops);

-- 5. kb_documents 新增 chunk_count 字段
ALTER TABLE kb_documents ADD COLUMN chunk_count INT DEFAULT 0;

-- 6. knowledge_bases 新增 embedding_model 和 embedding_dimensions 字段
ALTER TABLE knowledge_bases ADD COLUMN embedding_model VARCHAR(100) NOT NULL DEFAULT 'text-embedding-3-small';
ALTER TABLE knowledge_bases ADD COLUMN embedding_dimensions INT;
```

- [ ] **Step 2: 确认 PostgreSQL 已安装 pgvector 扩展**

运行（在 PostgreSQL 服务器上）：
```sql
SELECT * FROM pg_available_extensions WHERE name = 'vector';
```
如果未安装，需要先安装 pgvector：
```bash
# Ubuntu/Debian
sudo apt install postgresql-16-pgvector
# 或从源码编译：https://github.com/pgvector/pgvector
```

- [ ] **Step 3: 启动应用验证迁移**

```bash
cd backend && mvn spring-boot:run
```
预期：Flyway 自动执行 V8，日志显示 `Successfully applied 1 migration`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V8__rag_vector_search.sql
git commit -m "feat: V8 migration - pgvector + kb_chunks + embedding configs tables"
```

---

## Task 2: 后端模型和 Mapper

**Files:**
- Create: `backend/src/main/java/com/devknowledge/model/UserEmbeddingConfig.java`
- Create: `backend/src/main/java/com/devknowledge/model/KbChunk.java`
- Create: `backend/src/main/java/com/devknowledge/model/EmbeddingUsage.java`
- Create: `backend/src/main/java/com/devknowledge/mapper/UserEmbeddingConfigMapper.java`
- Create: `backend/src/main/java/com/devknowledge/mapper/KbChunkMapper.java`
- Create: `backend/src/main/java/com/devknowledge/mapper/EmbeddingUsageMapper.java`
- Create: `backend/src/main/java/com/devknowledge/dto/EmbeddingConfigRequest.java`
- Create: `backend/src/main/java/com/devknowledge/dto/EmbeddingConfigResponse.java`
- Create: `backend/src/main/java/com/devknowledge/dto/KbChunkSearchResult.java`
- Modify: `backend/src/main/java/com/devknowledge/model/KnowledgeBase.java`
- Modify: `backend/src/main/java/com/devknowledge/dto/KbCreateRequest.java`

- [ ] **Step 1: UserEmbeddingConfig 实体**

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
@TableName("user_embedding_configs")
public class UserEmbeddingConfig {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    private String name;
    private String apiKey;
    private String baseUrl;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
```

- [ ] **Step 2: KbChunk 实体**

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
@TableName("kb_chunks")
public class KbChunk {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID kbId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID docId;

    private Integer chunkIndex;
    private String content;
    // embedding 由自定义 SQL 处理，不映射为 Java 字段
    private Instant createdAt;
}
```

- [ ] **Step 3: EmbeddingUsage 实体**

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
@TableName("embedding_usage")
public class EmbeddingUsage {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID configId;

    private Integer promptTokens;
    private Instant createdAt;
}
```

- [ ] **Step 4: KnowledgeBase 实体新增字段**

在 `KnowledgeBase.java` 末尾 `}` 前新增：

```java
    /** Embedding 模型名（创建时锁定） */
    private String embeddingModel;

    /** Embedding 维度（创建时锁定，可为 null） */
    private Integer embeddingDimensions;
```

- [ ] **Step 5: KbCreateRequest 新增字段**

```java
package com.devknowledge.dto;

import lombok.Data;

@Data
public class KbCreateRequest {
    private String name;
    private String description;
    /** Embedding 模型（text-embedding-3-small / large / ada-002） */
    private String embeddingModel;
    /** 向量维度（可选，仅 small/large 支持） */
    private Integer embeddingDimensions;
}
```

- [ ] **Step 6: DTO 文件**

`EmbeddingConfigRequest.java`:
```java
package com.devknowledge.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class EmbeddingConfigRequest {
    private UUID configId;
    private String name;
    private String apiKey;
    private String baseUrl;
}
```

`EmbeddingConfigResponse.java`:
```java
package com.devknowledge.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class EmbeddingConfigResponse {
    private UUID id;
    private String name;
    private String apiKeyMasked;
    private String baseUrl;
    private Boolean isActive;

    @Data
    @lombok.AllArgsConstructor
    public static class TestResult {
        private boolean success;
        private String message;
    }
}
```

`KbChunkSearchResult.java`:
```java
package com.devknowledge.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class KbChunkSearchResult {
    private UUID id;
    private UUID docId;
    private String filename;
    private Integer chunkIndex;
    private String content;
    private double score;
}
```

- [ ] **Step 7: Mapper 文件**

`UserEmbeddingConfigMapper.java`:
```java
package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.UserEmbeddingConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserEmbeddingConfigMapper extends BaseMapper<UserEmbeddingConfig> {
}
```

`KbChunkMapper.java`:
```java
package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.dto.KbChunkSearchResult;
import com.devknowledge.model.KbChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    /**
     * pgvector 余弦相似度检索
     * 1 - (embedding <=> vector) = 余弦相似度（0-1）
     */
    @Select("SELECT c.id, c.doc_id as docId, d.filename, c.chunk_index as chunkIndex, c.content, " +
            "1 - (c.embedding <=> #{vector}::vector) as score " +
            "FROM kb_chunks c " +
            "JOIN kb_documents d ON c.doc_id = d.id " +
            "WHERE c.kb_id = #{kbId} " +
            "ORDER BY c.embedding <=> #{vector}::vector " +
            "LIMIT #{topK}")
    List<KbChunkSearchResult> searchByVector(
            @Param("kbId") UUID kbId,
            @Param("vector") String vectorLiteral,
            @Param("topK") int topK);
}
```

`EmbeddingUsageMapper.java`:
```java
package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.EmbeddingUsage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmbeddingUsageMapper extends BaseMapper<EmbeddingUsage> {
}
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/devknowledge/model/UserEmbeddingConfig.java \
        backend/src/main/java/com/devknowledge/model/KbChunk.java \
        backend/src/main/java/com/devknowledge/model/EmbeddingUsage.java \
        backend/src/main/java/com/devknowledge/model/KnowledgeBase.java \
        backend/src/main/java/com/devknowledge/mapper/ \
        backend/src/main/java/com/devknowledge/dto/EmbeddingConfigRequest.java \
        backend/src/main/java/com/devknowledge/dto/EmbeddingConfigResponse.java \
        backend/src/main/java/com/devknowledge/dto/KbChunkSearchResult.java \
        backend/src/main/java/com/devknowledge/dto/KbCreateRequest.java
git commit -m "feat: add embedding config, chunk, and usage models + mappers + DTOs"
```

---

## Task 3: EmbeddingService（核心 Embedding 能力）

**Files:**
- Create: `backend/src/main/java/com/devknowledge/service/EmbeddingService.java`

- [ ] **Step 1: 创建 EmbeddingService**

```java
package com.devknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int VECTOR_DIMENSION = 1536;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 批量 Embedding
     *
     * @param texts      文本列表（每批最多 20 个）
     * @param baseUrl    API 地址（如 https://api.openai.com/v1）
     * @param apiKey     API Key
     * @param model      模型名
     * @param dimensions 可选维度压缩（null = 模型默认）
     * @return EmbeddingResult（向量列表 + promptTokens）
     */
    public EmbeddingResult embedBatch(List<String> texts, String baseUrl, String apiKey,
                                       String model, Integer dimensions) {
        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts.size() == 1 ? texts.get(0) : texts);
        if (dimensions != null) {
            body.put("dimensions", dimensions);
        }

        log.info("Embedding 请求: model={}, texts={}, dimensions={}", model, texts.size(), dimensions);

        try {
            String responseStr = client.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode data = root.get("data");
            int promptTokens = root.has("usage")
                    ? root.get("usage").get("prompt_tokens").asInt()
                    : 0;

            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.get("embedding");
                float[] raw = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    raw[i] = (float) embeddingNode.get(i).asDouble();
                }
                vectors.add(padToTargetDimension(raw, VECTOR_DIMENSION));
            }

            log.info("Embedding 完成: {} 个向量, promptTokens={}", vectors.size(), promptTokens);
            return new EmbeddingResult(vectors, promptTokens);

        } catch (Exception e) {
            String msg = e.getMessage();
            log.error("Embedding 调用失败: {}", msg);
            if (msg != null && msg.contains("401")) {
                throw new RuntimeException("Embedding API Key 无效");
            }
            if (msg != null && msg.contains("Not supported model")) {
                throw new RuntimeException("不支持的 Embedding 模型: " + model);
            }
            throw new RuntimeException("Embedding 调用失败: " + msg);
        }
    }

    /**
     * 单条 Embedding（便捷方法）
     */
    public float[] embed(String text, String baseUrl, String apiKey,
                          String model, Integer dimensions) {
        return embedBatch(List.of(text), baseUrl, apiKey, model, dimensions).vectors().get(0);
    }

    /**
     * 将 float[] 转为 pgvector 可接受的字符串格式: "[0.1, 0.2, ...]"
     */
    public static String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private float[] padToTargetDimension(float[] original, int targetDim) {
        if (original.length == targetDim) return original;
        float[] padded = new float[targetDim];
        System.arraycopy(original, 0, padded, 0, Math.min(original.length, targetDim));
        return padded;
    }

    /**
     * 测试 Embedding API 连通性
     */
    public EmbeddingConfigResponse.TestResult testConnection(String baseUrl, String apiKey) {
        try {
            embed("test", baseUrl, apiKey, "text-embedding-3-small", null);
            return new EmbeddingConfigResponse.TestResult(true, "连接成功！");
        } catch (Exception e) {
            return new EmbeddingConfigResponse.TestResult(false, "连接失败: " + e.getMessage());
        }
    }

    /** Embedding 结果：向量列表 + token 消耗 */
    public record EmbeddingResult(List<float[]> vectors, int promptTokens) {}
}
```

注意：`testConnection` 方法引用了 `EmbeddingConfigResponse.TestResult`，需要在 Task 2 的 DTO 中已创建。如果编译报错，将 `TestResult` 内联为 `record TestResult(boolean success, String message)` 临时替代。

- [ ] **Step 2: 验证编译**

```bash
cd backend && mvn compile -q
```
预期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/EmbeddingService.java
git commit -m "feat: EmbeddingService - OpenAI /v1/embeddings with batch support and dimension padding"
```

---

## Task 4: EmbeddingConfigService + Token 消耗 + Controller

**Files:**
- Create: `backend/src/main/java/com/devknowledge/service/EmbeddingConfigService.java`
- Create: `backend/src/main/java/com/devknowledge/service/EmbeddingUsageService.java`
- Create: `backend/src/main/java/com/devknowledge/controller/EmbeddingConfigController.java`

- [ ] **Step 1: EmbeddingUsageService**

```java
package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.mapper.EmbeddingUsageMapper;
import com.devknowledge.model.EmbeddingUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmbeddingUsageService {

    private final EmbeddingUsageMapper usageMapper;

    public void recordUsage(UUID userId, UUID configId, int promptTokens) {
        EmbeddingUsage usage = new EmbeddingUsage();
        usage.setId(UUID.randomUUID());
        usage.setUserId(userId);
        usage.setConfigId(configId);
        usage.setPromptTokens(promptTokens);
        usage.setCreatedAt(Instant.now());
        usageMapper.insert(usage);
    }

    public List<AiConfigResponse.TokenUsage> getWeeklyUsage(UUID userId) {
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));
        List<EmbeddingUsage> usages = usageMapper.selectList(
                new LambdaQueryWrapper<EmbeddingUsage>()
                        .eq(EmbeddingUsage::getUserId, userId)
                        .ge(EmbeddingUsage::getCreatedAt, sevenDaysAgo)
                        .orderByAsc(EmbeddingUsage::getCreatedAt));

        Map<String, Long> dailyUsage = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String date = Instant.now().minus(Duration.ofDays(i))
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().toString();
            dailyUsage.put(date, 0L);
        }

        for (EmbeddingUsage u : usages) {
            String date = u.getCreatedAt()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().toString();
            dailyUsage.merge(date, u.getPromptTokens() != null ? u.getPromptTokens().longValue() : 0L, Long::sum);
        }

        return dailyUsage.entrySet().stream()
                .map(e -> new AiConfigResponse.TokenUsage(e.getKey(), e.getValue()))
                .toList();
    }
}
```

- [ ] **Step 2: EmbeddingConfigService**

参照 `AiConfigService` 的模式实现。核心方法：

```java
package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.EmbeddingConfigRequest;
import com.devknowledge.dto.EmbeddingConfigResponse;
import com.devknowledge.mapper.UserEmbeddingConfigMapper;
import com.devknowledge.model.UserEmbeddingConfig;
import com.devknowledge.security.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmbeddingConfigService {

    private final UserEmbeddingConfigMapper configMapper;
    private final EmbeddingService embeddingService;
    private final EmbeddingUsageService usageService;

    @Value("${jwt.secret}")
    private String aesSecret;

    /**
     * 获取激活的 Embedding 配置（内部使用，原始实体）
     */
    public UserEmbeddingConfig getActiveConfig(UUID userId) {
        return configMapper.selectOne(
                new LambdaQueryWrapper<UserEmbeddingConfig>()
                        .eq(UserEmbeddingConfig::getUserId, userId)
                        .eq(UserEmbeddingConfig::getIsActive, true));
    }

    /**
     * 获取激活配置（脱敏）
     */
    public Mono<EmbeddingConfigResponse> getActiveConfigResponse(UUID userId) {
        return Mono.fromCallable(() -> {
            UserEmbeddingConfig config = getActiveConfig(userId);
            return config != null ? toResponse(config) : null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取所有配置列表
     */
    public Mono<List<EmbeddingConfigResponse>> getAllConfigs(UUID userId) {
        return Mono.fromCallable(() -> {
            List<UserEmbeddingConfig> configs = configMapper.selectList(
                    new LambdaQueryWrapper<UserEmbeddingConfig>()
                            .eq(UserEmbeddingConfig::getUserId, userId)
                            .orderByDesc(UserEmbeddingConfig::getIsActive)
                            .orderByDesc(UserEmbeddingConfig::getUpdatedAt));
            return configs.stream().map(this::toResponse).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 创建或更新 Embedding 配置
     */
    public Mono<EmbeddingConfigResponse> updateConfig(UUID userId, EmbeddingConfigRequest req) {
        return Mono.fromCallable(() -> {
            AesUtil aes = new AesUtil(aesSecret);
            Instant now = Instant.now();
            UserEmbeddingConfig target = null;

            if (req.getConfigId() != null) {
                target = configMapper.selectById(req.getConfigId());
                if (target != null && !target.getUserId().equals(userId)) {
                    throw new RuntimeException("无权修改此配置");
                }
            }

            if (target != null) {
                target.setName(req.getName());
                target.setBaseUrl(req.getBaseUrl());
                target.setUpdatedAt(now);
                if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
                    target.setApiKey(aes.encrypt(req.getApiKey()));
                }
                configMapper.updateById(target);
            } else {
                if (req.getApiKey() == null || req.getApiKey().isBlank()) {
                    throw new RuntimeException("新建配置必须提供 API Key");
                }
                deactivateAll(userId);
                target = new UserEmbeddingConfig();
                target.setId(UUID.randomUUID());
                target.setUserId(userId);
                target.setName(req.getName() != null ? req.getName() : "OpenAI Embedding");
                target.setApiKey(aes.encrypt(req.getApiKey()));
                target.setBaseUrl(req.getBaseUrl() != null ? req.getBaseUrl() : "https://api.openai.com/v1");
                target.setIsActive(true);
                target.setCreatedAt(now);
                target.setUpdatedAt(now);
                configMapper.insert(target);
            }

            activateConfig(userId, target.getId());
            return toResponse(target);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> switchConfig(UUID userId, UUID configId) {
        return Mono.fromRunnable(() -> {
            UserEmbeddingConfig config = configMapper.selectById(configId);
            if (config == null || !config.getUserId().equals(userId)) {
                throw new RuntimeException("配置不存在");
            }
            activateConfig(userId, configId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> deleteConfig(UUID userId, UUID configId) {
        return Mono.fromRunnable(() -> {
            UserEmbeddingConfig config = configMapper.selectById(configId);
            if (config == null || !config.getUserId().equals(userId)) {
                throw new RuntimeException("配置不存在");
            }
            long count = configMapper.selectCount(
                    new LambdaQueryWrapper<UserEmbeddingConfig>()
                            .eq(UserEmbeddingConfig::getUserId, userId));
            if (count <= 1) {
                throw new RuntimeException("至少保留一个 Embedding 配置");
            }
            boolean wasActive = Boolean.TRUE.equals(config.getIsActive());
            configMapper.deleteById(configId);
            if (wasActive) {
                UserEmbeddingConfig next = configMapper.selectOne(
                        new LambdaQueryWrapper<UserEmbeddingConfig>()
                                .eq(UserEmbeddingConfig::getUserId, userId)
                                .last("LIMIT 1"));
                if (next != null) activateConfig(userId, next.getId());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 测试连通性
     */
    public Mono<EmbeddingConfigResponse.TestResult> testConfig(UUID userId) {
        return Mono.fromCallable(() -> {
            UserEmbeddingConfig config = getActiveConfig(userId);
            if (config == null) {
                throw new RuntimeException("请先配置 Embedding AI");
            }
            AesUtil aes = new AesUtil(aesSecret);
            String apiKey = aes.decrypt(config.getApiKey());
            return embeddingService.testConnection(config.getBaseUrl(), apiKey);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取 Token 消耗统计
     */
    public Mono<List<AiConfigResponse.TokenUsage>> getEmbeddingUsage(UUID userId) {
        return Mono.fromCallable(() -> usageService.getWeeklyUsage(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 内部方法 ====================

    private void deactivateAll(UUID userId) {
        UserEmbeddingConfig deactivate = new UserEmbeddingConfig();
        deactivate.setIsActive(false);
        configMapper.update(deactivate,
                new LambdaQueryWrapper<UserEmbeddingConfig>()
                        .eq(UserEmbeddingConfig::getUserId, userId)
                        .eq(UserEmbeddingConfig::getIsActive, true));
    }

    private void activateConfig(UUID userId, UUID configId) {
        UserEmbeddingConfig activate = new UserEmbeddingConfig();
        activate.setId(configId);
        activate.setIsActive(true);
        configMapper.updateById(activate);
    }

    private EmbeddingConfigResponse toResponse(UserEmbeddingConfig config) {
        AesUtil aes = new AesUtil(aesSecret);
        EmbeddingConfigResponse resp = new EmbeddingConfigResponse();
        resp.setId(config.getId());
        resp.setName(config.getName());
        resp.setBaseUrl(config.getBaseUrl());
        resp.setIsActive(Boolean.TRUE.equals(config.getIsActive()));
        try {
            String plainKey = aes.decrypt(config.getApiKey());
            resp.setApiKeyMasked(AesUtil.mask(plainKey));
        } catch (Exception e) {
            resp.setApiKeyMasked("****");
        }
        return resp;
    }
}
```

- [ ] **Step 3: EmbeddingConfigController**

```java
package com.devknowledge.controller;

import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.dto.EmbeddingConfigRequest;
import com.devknowledge.dto.EmbeddingConfigResponse;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.EmbeddingConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class EmbeddingConfigController {

    private final EmbeddingConfigService embeddingConfigService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/embedding-config")
    public Mono<ResponseEntity<EmbeddingConfigResponse>> getActive(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.getActiveConfigResponse(userId)
                .map(config -> config != null ? ResponseEntity.ok(config) : ResponseEntity.ok().build());
    }

    @GetMapping("/embedding-configs")
    public Mono<ResponseEntity<List<EmbeddingConfigResponse>>> getAll(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.getAllConfigs(userId).map(ResponseEntity::ok);
    }

    @PutMapping("/embedding-config")
    public Mono<ResponseEntity<EmbeddingConfigResponse>> update(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody EmbeddingConfigRequest req) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.updateConfig(userId, req).map(ResponseEntity::ok);
    }

    @PostMapping("/embedding-configs/{id}/activate")
    public Mono<ResponseEntity<Void>> activate(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.switchConfig(userId, id)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @DeleteMapping("/embedding-configs/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.deleteConfig(userId, id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @PostMapping("/embedding-config/test")
    public Mono<ResponseEntity<EmbeddingConfigResponse.TestResult>> test(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.testConfig(userId).map(ResponseEntity::ok);
    }

    @GetMapping("/embedding-usage")
    public Mono<ResponseEntity<List<AiConfigResponse.TokenUsage>>> getUsage(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.getEmbeddingUsage(userId).map(ResponseEntity::ok);
    }

    private UUID extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
```

- [ ] **Step 4: 验证编译**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/EmbeddingConfigService.java \
        backend/src/main/java/com/devknowledge/service/EmbeddingUsageService.java \
        backend/src/main/java/com/devknowledge/controller/EmbeddingConfigController.java
git commit -m "feat: EmbeddingConfigService + Controller - CRUD, test, token usage"
```

---

## Task 5: KbService 改造（文档切分 + 向量检索）

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/KbService.java`
- Modify: `backend/src/main/java/com/devknowledge/dto/KbCreateRequest.java`（已在 Task 2 完成）

- [ ] **Step 1: 在 KbService 中新增依赖注入**

在 `KbService` 类中新增字段：

```java
    private final EmbeddingService embeddingService;
    private final EmbeddingConfigService embeddingConfigService;
    private final EmbeddingUsageService embeddingUsageService;
    private final KbChunkMapper chunkMapper;

    @Value("${jwt.secret}")
    private String aesSecret;
```

需要新增 import：
```java
import com.devknowledge.dto.KbChunkSearchResult;
import com.devknowledge.mapper.KbChunkMapper;
import com.devknowledge.model.KbChunk;
import com.devknowledge.model.UserEmbeddingConfig;
import com.devknowledge.security.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.List;
```

- [ ] **Step 2: 修改 createKb 方法，传递 embeddingModel 和 embeddingDimensions**

将现有 `createKb` 方法改为：

```java
public Mono<KnowledgeBase> createKb(UUID userId, KbCreateRequest req) {
    return Mono.fromCallable(() -> {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(UUID.randomUUID());
        kb.setUserId(userId);
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setEmbeddingModel(req.getEmbeddingModel() != null
                ? req.getEmbeddingModel() : "text-embedding-3-small");
        kb.setEmbeddingDimensions(req.getEmbeddingDimensions());
        kb.setCreatedAt(Instant.now());
        kb.setUpdatedAt(Instant.now());
        kbMapper.insert(kb);
        return kb;
    }).subscribeOn(Schedulers.boundedElastic());
}
```

- [ ] **Step 3: 修改 uploadDocument，在解析完成后触发 chunkAndEmbed**

在现有 `uploadDocument` 方法的 `parseExecutor.submit()` 中，解析完成后调用 `chunkAndEmbed`：

```java
parseExecutor.submit(() -> {
    try {
        String text = fileParserService.parse(filename, content);
        doc.setContent(text);
        doc.setStatus("ready");
        log.info("文档解析完成: {} ({}字)", filename, text.length());
        docMapper.updateById(doc);

        // 触发切分 + 向量化
        try {
            chunkAndEmbed(kbId, doc.getId(), text);
            log.info("文档向量化完成: {}", filename);
        } catch (Exception embedEx) {
            log.warn("文档向量化失败（不影响文档可用）: {} - {}", filename, embedEx.getMessage());
        }
    } catch (Exception e) {
        doc.setStatus("error");
        doc.setErrorMessage(e.getMessage());
        log.error("文档解析失败: {} - {}", filename, e.getMessage());
        docMapper.updateById(doc);
    }
});
```

- [ ] **Step 4: 新增 chunkAndEmbed 方法**

```java
/**
 * 文档切分 + 向量化
 */
private void chunkAndEmbed(UUID kbId, UUID docId, String content) {
    KnowledgeBase kb = kbMapper.selectById(kbId);
    if (kb == null) return;

    // 从知识库读取锁定的模型和维度
    String model = kb.getEmbeddingModel();
    Integer dimensions = kb.getEmbeddingDimensions();

    // 加载用户 Embedding 配置
    UserEmbeddingConfig embedConfig = embeddingConfigService.getActiveConfig(kb.getUserId());
    if (embedConfig == null) {
        log.warn("用户未配置 Embedding AI，跳过向量化");
        return;
    }
    AesUtil aes = new AesUtil(aesSecret);
    String apiKey = aes.decrypt(embedConfig.getApiKey());

    List<String> chunks = splitIntoChunks(content);
    log.info("文档切分完成: {} 个 chunk", chunks.size());

    int totalTokens = 0;
    int chunkIndex = 0;

    for (List<String> batch : partition(chunks, 20)) {
        EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                batch, embedConfig.getBaseUrl(), apiKey, model, dimensions);
        totalTokens += result.promptTokens();

        for (int i = 0; i < batch.size(); i++) {
            KbChunk chunk = new KbChunk();
            chunk.setId(UUID.randomUUID());
            chunk.setKbId(kbId);
            chunk.setDocId(docId);
            chunk.setChunkIndex(chunkIndex++);
            chunk.setContent(batch.get(i));
            chunk.setCreatedAt(Instant.now());
            chunkMapper.insert(chunk);

            // 向量通过原生 SQL 更新
            String vectorStr = EmbeddingService.vectorToString(result.vectors().get(i));
            chunkMapper.updateVectorById(chunk.getId(), vectorStr);
        }

        if (chunks.size() > 20) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
    }

    // 记录 Token 消耗
    embeddingUsageService.recordUsage(kb.getUserId(), embedConfig.getId(), totalTokens);

    // 更新文档的 chunk_count
    doc.setChunkCount(chunks.size());
    docMapper.updateById(doc);
}
```

**注意**：`chunkMapper.updateVectorById` 需要在 `KbChunkMapper` 中新增自定义方法（因为 MyBatis Plus 不支持直接操作 pgvector 类型）。在 `KbChunkMapper` 中新增：

```java
@Update("UPDATE kb_chunks SET embedding = #{vector}::vector WHERE id = #{id}")
void updateVectorById(@Param("id") UUID id, @Param("vector") String vectorLiteral);
```

- [ ] **Step 5: 新增 splitIntoChunks 方法**

```java
/**
 * 段落切分
 * 规则：按 \n\n 分割，合并短段（<100字），截断长段（>1000字）
 */
private List<String> splitIntoChunks(String content) {
    if (content == null || content.isBlank()) return List.of();

    String[] rawParts = content.split("\\n\\n+");
    List<String> chunks = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();

    for (String part : rawParts) {
        String trimmed = part.trim();
        if (trimmed.isEmpty()) continue;

        if (buffer.length() + trimmed.length() < 100) {
            // 合并短段
            if (buffer.length() > 0) buffer.append("\n\n");
            buffer.append(trimmed);
        } else {
            if (buffer.length() > 0) {
                chunks.add(buffer.toString());
                buffer.setLength(0);
            }
            if (trimmed.length() > 1000) {
                // 截断长段：按句号/换行再切
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
            // 尝试在句号或换行处断开
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
```

- [ ] **Step 6: 新增 searchKbVector 方法**

```java
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

        EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                List.of(query), embedConfig.getBaseUrl(), apiKey,
                kb.getEmbeddingModel(), kb.getEmbeddingDimensions());
        float[] queryVector = result.vectors().get(0);

        embeddingUsageService.recordUsage(userId, embedConfig.getId(), result.promptTokens());

        String vectorStr = EmbeddingService.vectorToString(queryVector);
        return chunkMapper.searchByVector(kbId, vectorStr, topK);
    }).subscribeOn(Schedulers.boundedElastic());
}

/**
 * LIKE 回退搜索（Embedding 未配置时使用）
 */
private List<KbChunkSearchResult> searchKbFallback(UUID kbId, String query) {
    // 使用现有 LIKE 逻辑，包装为 KbChunkSearchResult
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
        r.setScore(0.5); // LIKE 无真实相似度
        results.add(r);
    }
    return results;
}
```

- [ ] **Step 7: 验证编译**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/KbService.java \
        backend/src/main/java/com/devknowledge/mapper/KbChunkMapper.java
git commit -m "feat: KbService - chunkAndEmbed paragraph splitting + searchKbVector pgvector"
```

---

## Task 6: DemoService 预检索注入 + DemoToolProvider 改造

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/DemoService.java`
- Modify: `backend/src/main/java/com/devknowledge/service/ai/DemoToolProvider.java`

- [ ] **Step 1: DemoService 注入 KbService 依赖**

在 `DemoService` 中新增：

```java
private final KbService kbService;
```

在 `GenerateDemoRequest` DTO 中新增 `topK` 字段：

```java
// 在 GenerateDemoRequest.java 中新增
private Integer topK;
```

- [ ] **Step 2: 在 generateDemo 中加入预检索注入**

在 `generateDemo` 方法中，`String systemPrompt = buildSystemPrompt(req);` 之后、ReAct Agent 之前，新增：

```java
// RAG 预检索注入
if (req.getKbId() != null) {
    int topK = req.getTopK() != null ? req.getTopK() : 3;
    try {
        List<KbChunkSearchResult> contextChunks =
                kbService.searchKbVector(userId, req.getKbId(), req.getPrompt(), topK).block();
        if (contextChunks != null && !contextChunks.isEmpty()) {
            systemPrompt += buildRagContext(contextChunks);
        }
    } catch (Exception e) {
        log.warn("RAG 预检索失败，继续无 RAG 生成: {}", e.getMessage());
    }

    // 保留 search_kb 工具供二次检索
    tools.add(toolProvider.getKbTool());
    handlers.put("search_kb", toolProvider.getKbHandler(userId, req.getKbId()));
}
```

- [ ] **Step 3: 新增 buildRagContext 方法**

```java
private String buildRagContext(List<KbChunkSearchResult> chunks) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n\n以下是知识库中的相关参考内容（已自动检索）：\n");
    sb.append("请优先参考这些内容回答问题，如果信息不足可以调用 search_kb 工具进一步搜索。\n\n");
    for (int i = 0; i < chunks.size(); i++) {
        KbChunkSearchResult chunk = chunks.get(i);
        sb.append(String.format("[%d] 来源: %s (相关度: %.0f%%)\n",
                i + 1,
                chunk.getFilename() != null ? chunk.getFilename() : "未知",
                chunk.getScore() * 100));
        sb.append(chunk.getContent()).append("\n\n");
    }
    return sb.toString();
}
```

需要 import：
```java
import com.devknowledge.dto.KbChunkSearchResult;
```

- [ ] **Step 4: DemoToolProvider 改造 search_kb handler**

将 `buildSearchKbHandler` 方法改为接受 `userId` 参数，使用向量检索：

```java
/**
 * 构建知识库工具处理器（向量检索版）
 */
public ToolHandler getKbHandler(UUID userId, UUID kbId) {
    return buildSearchKbHandler(userId, kbId);
}

private ToolHandler buildSearchKbHandler(UUID userId, UUID kbId) {
    return args -> {
        try {
            String query = extractJsonString(args, "query");
            log.info("工具 search_kb 执行，kbId={}, query={}", kbId, query);

            var results = kbService.searchKbVector(userId, kbId, query, 5).block();
            if (results == null || results.isEmpty()) return "知识库中未找到相关内容";

            StringBuilder sb = new StringBuilder();
            for (var chunk : results) {
                sb.append("【").append(chunk.getFilename() != null ? chunk.getFilename() : "文档").append("】");
                sb.append(" 相关度: ").append(String.format("%.0f%%", chunk.getScore() * 100)).append("\n");
                sb.append(chunk.getContent()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "知识库搜索失败: " + e.getMessage();
        }
    };
}
```

注意：原来的 `getKbHandler(UUID kbId)` 方法签名变了，调用方需要传入 `userId`。确保所有调用方已更新。

- [ ] **Step 5: 验证编译**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/DemoService.java \
        backend/src/main/java/com/devknowledge/service/ai/DemoToolProvider.java \
        backend/src/main/java/com/devknowledge/dto/GenerateDemoRequest.java
git commit -m "feat: RAG pre-retrieval injection in DemoService + vector search in DemoToolProvider"
```

---

## Task 7: 前端类型 + API 客户端

**Files:**
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/api/settings.ts`
- Create: `frontend/src/api/embedding.ts`
- Modify: `frontend/src/api/kb.ts`

- [ ] **Step 1: types/api.ts 新增类型**

在 `types/api.ts` 末尾新增：

```typescript
// Embedding Config
export interface EmbeddingConfig {
  id?: string
  name?: string
  apiKeyMasked: string
  baseUrl: string
  isActive?: boolean
}

export interface EmbeddingConfigRequest {
  configId?: string
  name?: string
  apiKey: string
  baseUrl: string
}
```

修改现有类型：

```typescript
// GenerateDemoRequest 新增 topK
export interface GenerateDemoRequest {
  prompt: string
  frameworkId?: string
  language?: string
  maxIterations?: number
  kbId?: string
  topK?: number          // 新增
}

// KbDocument 新增 chunkCount
export interface KbDocument {
  id: string
  kbId: string
  filename: string
  fileType: string
  fileSize: number
  content?: string
  status: 'processing' | 'ready' | 'error' | 'embedding'
  errorMessage?: string
  chunkCount?: number     // 新增
  createdAt: string
}

// KnowledgeBase 新增 embeddingModel 和 embeddingDimensions
export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  documentCount?: number
  embeddingModel?: string       // 新增
  embeddingDimensions?: number  // 新增
  createdAt: string
  updatedAt: string
}
```

- [ ] **Step 2: 创建 embedding.ts API 客户端**

```typescript
// frontend/src/api/embedding.ts
import { api } from './client'
import type { EmbeddingConfig, EmbeddingConfigRequest, TokenUsage } from '@/types/api'

export const embeddingApi = {
  getActiveConfig: () =>
    api.get<EmbeddingConfig>('/user/embedding-config'),

  getAllConfigs: () =>
    api.get<EmbeddingConfig[]>('/user/embedding-configs'),

  updateConfig: (data: EmbeddingConfigRequest) =>
    api.put<EmbeddingConfig>('/user/embedding-config', data),

  switchConfig: (id: string) =>
    api.post(`/user/embedding-configs/${id}/activate`),

  deleteConfig: (id: string) =>
    api.delete(`/user/embedding-configs/${id}`),

  testConfig: () =>
    api.post<{ success: boolean; message: string }>('/user/embedding-config/test'),

  getTokenUsage: () =>
    api.get<TokenUsage[]>('/user/embedding-usage'),
}
```

- [ ] **Step 3: 修改 kb.ts - createKb 参数**

```typescript
// 修改 createKb
createKb: (data: { name: string; description?: string; embeddingModel?: string; embeddingDimensions?: number }) =>
  api.post<KnowledgeBase>('/kb', data),
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/api.ts \
        frontend/src/api/embedding.ts \
        frontend/src/api/kb.ts
git commit -m "feat: frontend types + API client for embedding config and vector search"
```

---

## Task 8: 前端 SettingsPage 侧边栏 + EmbeddingSettings 页面

**Files:**
- Modify: `frontend/src/pages/SettingsPage.tsx`
- Create: `frontend/src/pages/settings/EmbeddingSettings.tsx`

- [ ] **Step 1: SettingsPage 改为侧边栏导航**

```tsx
// frontend/src/pages/SettingsPage.tsx
import { useState } from 'react'
import { useAuthStore } from '@/stores/authStore'
import { AiSettings } from './settings/AiSettings'
import { EmbeddingSettings } from './settings/EmbeddingSettings'
import { StorageSettings } from './settings/StorageSettings'

type SettingsTab = 'ai' | 'embedding' | 'storage'

const tabs: { key: SettingsTab; label: string; desc: string }[] = [
  { key: 'ai', label: 'AI 服务配置', desc: 'Chat 模型配置' },
  { key: 'embedding', label: 'Embedding AI', desc: '文本向量化模型' },
  { key: 'storage', label: '数据存储', desc: '本地存储设置' },
]

export function SettingsPage() {
  const { isAuthenticated } = useAuthStore()
  const [activeTab, setActiveTab] = useState<SettingsTab>('ai')

  if (!isAuthenticated) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-gray-900 mb-4">设置</h1>
        <p className="text-gray-500">请先登录以配置服务。</p>
      </div>
    )
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">设置</h1>

      <div className="flex gap-6">
        {/* 侧边栏 */}
        <nav className="w-48 flex-shrink-0">
          <div className="space-y-1">
            {tabs.map(tab => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-colors ${
                  activeTab === tab.key
                    ? 'bg-primary-50 text-primary-700 font-medium'
                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                }`}
              >
                <div>{tab.label}</div>
                <div className="text-xs text-gray-400 mt-0.5">{tab.desc}</div>
              </button>
            ))}
          </div>
        </nav>

        {/* 内容区 */}
        <div className="flex-1 min-w-0">
          {activeTab === 'ai' && <AiSettings />}
          {activeTab === 'embedding' && <EmbeddingSettings />}
          {activeTab === 'storage' && <StorageSettings />}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: 创建 EmbeddingSettings 页面**

参照 `AiSettings.tsx` 的 UI 模式，但简化（无 provider 选择、无 model 选择）：

```tsx
// frontend/src/pages/settings/EmbeddingSettings.tsx
import { useState, useEffect } from 'react'
import { embeddingApi } from '@/api/embedding'
import { useNotify } from '@/stores/notify'
import type { EmbeddingConfig, EmbeddingConfigRequest, TokenUsage } from '@/types/api'

export function EmbeddingSettings() {
  const { notify } = useNotify()
  const [configs, setConfigs] = useState<EmbeddingConfig[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [isNew, setIsNew] = useState(false)

  const [name, setName] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('https://api.openai.com/v1')
  const [maskedKey, setMaskedKey] = useState('')

  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [testing, setTesting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [tokenUsage, setTokenUsage] = useState<TokenUsage[]>([])

  useEffect(() => {
    loadConfigs()
    embeddingApi.getTokenUsage().then(setTokenUsage).catch(console.error)
  }, [])

  const loadConfigs = () => {
    embeddingApi.getAllConfigs().then(list => {
      setConfigs(list)
      if (!selectedId) {
        const active = list.find(c => c.isActive) || list[0]
        if (active) selectConfig(active)
      }
    }).catch(console.error)
  }

  const selectConfig = (config: EmbeddingConfig) => {
    setSelectedId(config.id || null)
    setIsNew(false)
    setName(config.name || '')
    setBaseUrl(config.baseUrl)
    setMaskedKey(config.apiKeyMasked)
    setApiKey('')
    setTestResult(null)
  }

  const handleNew = () => {
    setSelectedId(null)
    setIsNew(true)
    setName('')
    setBaseUrl('https://api.openai.com/v1')
    setApiKey('')
    setMaskedKey('')
    setTestResult(null)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const data: EmbeddingConfigRequest = {
        configId: selectedId || undefined,
        name: name || 'OpenAI Embedding',
        apiKey,
        baseUrl,
      }
      const saved = await embeddingApi.updateConfig(data)
      setMaskedKey(saved.apiKeyMasked)
      setApiKey('')
      setIsNew(false)
      setSelectedId(saved.id || null)
      loadConfigs()
      notify('Embedding 配置已保存', 'success')
    } catch (err) {
      notify(err instanceof Error ? err.message : '保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  const handleTest = async () => {
    setTesting(true)
    try {
      const res = await embeddingApi.testConfig()
      setTestResult(res)
    } catch (err) {
      setTestResult({ success: false, message: err instanceof Error ? err.message : '测试失败' })
    } finally {
      setTesting(false)
    }
  }

  const handleDelete = async () => {
    if (!selectedId) return
    if (!confirm('确定删除此配置？')) return
    try {
      await embeddingApi.deleteConfig(selectedId)
      setSelectedId(null)
      setIsNew(true)
      loadConfigs()
      notify('配置已删除', 'success')
    } catch (err) {
      notify(err instanceof Error ? err.message : '删除失败', 'error')
    }
  }

  const handleActivate = async (id: string) => {
    try {
      await embeddingApi.switchConfig(id)
      loadConfigs()
      notify('已切换', 'success')
    } catch (err) {
      notify(err instanceof Error ? err.message : '切换失败', 'error')
    }
  }

  const maxTokenValue = tokenUsage.reduce((max, d) => Math.max(max, d.tokens), 0)
  const totalTokens = tokenUsage.reduce((sum, d) => sum + d.tokens, 0)

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <h2 className="text-lg font-bold text-gray-900">Embedding AI 配置</h2>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 左侧：配置列表 */}
        <div className="border border-gray-200 rounded-lg p-4">
          <h3 className="text-sm font-medium text-gray-500 mb-3">我的 Embedding</h3>
          <div className="space-y-2">
            {configs.map(config => (
              <button
                key={config.id}
                onClick={() => selectConfig(config)}
                className={`w-full text-left p-3 rounded-lg border transition-all ${
                  selectedId === config.id
                    ? 'border-primary-500 bg-primary-50'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-900">{config.name || 'OpenAI Embedding'}</span>
                  {config.isActive && (
                    <span className="text-xs px-2 py-0.5 bg-green-100 text-green-700 rounded-full">使用中</span>
                  )}
                </div>
                <p className="text-xs text-gray-500 mt-1">{config.baseUrl}</p>
              </button>
            ))}
            <button
              onClick={handleNew}
              className="w-full p-3 border border-dashed border-gray-300 rounded-lg text-sm text-gray-500 hover:border-primary-400 hover:text-primary-600 transition-colors"
            >
              + 添加新配置
            </button>
          </div>
        </div>

        {/* 右侧：配置表单 */}
        <div className="lg:col-span-2 border border-gray-200 rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-medium text-gray-500">
              {isNew ? '新建配置' : '配置详情'}
            </h3>
            {isNew && configs.length > 0 && (
              <button
                onClick={() => { setIsNew(false); const first = configs.find(c => c.isActive) || configs[0]; if (first) selectConfig(first) }}
                className="text-xs text-gray-400 hover:text-gray-600 transition-colors"
              >
                ← 返回
              </button>
            )}
          </div>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">配置名称</label>
              <input type="text" value={name} onChange={e => setName(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">API Base URL</label>
              <input type="text" value={baseUrl} onChange={e => setBaseUrl(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">API Key</label>
              {maskedKey && !isNew && <p className="text-xs text-gray-400 mb-1">当前: {maskedKey}</p>}
              <input type="password" value={apiKey} onChange={e => setApiKey(e.target.value)}
                placeholder={isNew ? '输入 OpenAI API Key' : '留空则不更新'}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm" />
            </div>

            <div className="bg-gray-50 rounded-md p-3 text-xs text-gray-500">
              <p className="font-medium text-gray-700 mb-1">最佳实践</p>
              <p>- 推荐 text-embedding-3-small + dimensions=512，兼顾成本和质量</p>
              <p>- 批量处理：系统每 20 个文本片段一次 API 调用</p>
              <p>- 模型和维度在创建知识库时选择，此处仅管理 API 凭证</p>
            </div>

            <div className="flex gap-3">
              <button onClick={handleSave} disabled={saving}
                className="px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 disabled:opacity-50">
                {saving ? '保存中...' : '保存'}
              </button>
              <button onClick={handleTest} disabled={testing || isNew}
                title={isNew ? '请先保存配置后再测试' : ''}
                className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed">
                {testing ? '测试中...' : '测试连接'}
              </button>
              {!isNew && selectedId && !configs.find(c => c.id === selectedId)?.isActive && (
                <button onClick={() => handleActivate(selectedId!)}
                  className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium hover:bg-gray-50">
                  设为默认
                </button>
              )}
              {!isNew && configs.length > 1 && (
                <button onClick={handleDelete}
                  className="px-4 py-2 border border-red-200 text-red-600 rounded-md text-sm font-medium hover:bg-red-50 ml-auto">
                  删除
                </button>
              )}
            </div>

            {testResult && (
              <div className={`p-3 rounded-md text-sm ${testResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
                {testResult.message}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Token 消耗柱状图 */}
      <div className="mt-8 border border-gray-200 rounded-lg p-6">
        <h3 className="text-sm font-medium text-gray-500 mb-1">Embedding Token 消耗（近 7 天）</h3>
        <p className="text-xs text-gray-400 mb-4">总计: {totalTokens.toLocaleString()} tokens</p>
        {totalTokens === 0 ? (
          <p className="text-sm text-gray-400 py-8 text-center">暂无数据</p>
        ) : (
          <div className="flex items-end gap-2 h-40">
            {tokenUsage.map((d, i) => {
              const height = maxTokenValue > 0 ? (d.tokens / maxTokenValue) * 100 : 0
              return (
                <div key={i} className="flex-1 flex flex-col items-center gap-1 group">
                  <span className="text-xs text-gray-400 opacity-0 group-hover:opacity-100 transition-opacity">
                    {d.tokens.toLocaleString()}
                  </span>
                  <div className="w-full flex items-end" style={{ height: '120px' }}>
                    <div className="w-full bg-primary-500 rounded-t transition-all group-hover:bg-primary-600"
                      style={{ height: `${Math.max(height, 2)}%` }} />
                  </div>
                  <span className="text-xs text-gray-500">{d.date.slice(5)}</span>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 3: 验证前端编译**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
预期：无 TypeScript 错误

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/SettingsPage.tsx \
        frontend/src/pages/settings/EmbeddingSettings.tsx
git commit -m "feat: SettingsPage sidebar navigation + EmbeddingSettings page"
```

---

## Task 9: 前端 KbPage 改造 + DemoPage Top-K 滑块

**Files:**
- Modify: `frontend/src/pages/KbPage.tsx`
- Modify: `frontend/src/pages/DemoPage.tsx`

- [ ] **Step 1: KbPage - 创建知识库表单新增模型和维度选择**

在 KbPage 的创建知识库对话框中，新增 Embedding 模型和维度字段。具体位置在现有的 name/description 输入之后：

```tsx
{/* Embedding 模型选择 */}
<div>
  <label className="block text-sm font-medium text-gray-700 mb-1">Embedding 模型</label>
  <select
    value={embeddingModel}
    onChange={e => {
      setEmbeddingModel(e.target.value)
      // ada-002 不支持 dimensions
      if (e.target.value === 'text-embedding-ada-002') setEmbeddingDimensions(undefined)
    }}
    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
  >
    <option value="text-embedding-3-small">text-embedding-3-small（推荐，成本最低）</option>
    <option value="text-embedding-3-large">text-embedding-3-large（效果最好）</option>
    <option value="text-embedding-ada-002">text-embedding-ada-002（上一代）</option>
  </select>
</div>

{/* 向量维度 */}
<div>
  <label className="block text-sm font-medium text-gray-700 mb-1">
    向量维度
    <span className="text-xs text-gray-400 ml-2">（可选，创建后不可更改）</span>
  </label>
  <input
    type="number"
    value={embeddingDimensions || ''}
    onChange={e => setEmbeddingDimensions(e.target.value ? Number(e.target.value) : undefined)}
    placeholder={embeddingModel === 'text-embedding-3-small' ? '推荐 512，留空=1536' :
                 embeddingModel === 'text-embedding-3-large' ? '必须填 1536' :
                 'ada-002 不支持 dimensions'}
    disabled={embeddingModel === 'text-embedding-ada-002'}
    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-100 disabled:text-gray-400"
  />
  {embeddingModel === 'text-embedding-3-large' && (
    <p className="text-xs text-amber-600 mt-1">large 模型必须设置 dimensions=1536</p>
  )}
</div>
```

需要在 KbPage 组件中新增状态：

```typescript
const [embeddingModel, setEmbeddingModel] = useState('text-embedding-3-small')
const [embeddingDimensions, setEmbeddingDimensions] = useState<number | undefined>(undefined)
```

修改 `createKb` 调用：

```typescript
await kbApi.createKb({
  name: newKbName,
  description: newKbDesc,
  embeddingModel,
  embeddingDimensions,
})
```

在创建知识库的提交前加校验：

```typescript
if (embeddingModel === 'text-embedding-3-large' && embeddingDimensions !== 1536) {
  notify('large 模型必须设置 dimensions=1536', 'error')
  return
}
```

在知识库详情页显示锁定的模型信息：

```tsx
{kb.embeddingModel && (
  <p className="text-xs text-gray-400 mt-1">
    模型: {kb.embeddingModel}
    {kb.embeddingDimensions ? ` | 维度: ${kb.embeddingDimensions}` : ''}
  </p>
)}
```

- [ ] **Step 2: KbPage - 文档列表显示 chunk 数量**

在文档列表的表格/卡片中新增一列：

```tsx
{doc.chunkCount != null && doc.chunkCount > 0 && (
  <span className="text-xs text-gray-400 ml-2">{doc.chunkCount} chunks</span>
)}
```

- [ ] **Step 3: KbPage - 文档状态增加 embedding**

在状态 badge 渲染中新增：

```tsx
{doc.status === 'embedding' && (
  <span className="text-xs px-2 py-0.5 bg-blue-100 text-blue-700 rounded-full">向量化中</span>
)}
```

- [ ] **Step 4: DemoPage - 新增 Top-K 滑块**

在 DemoPage 的知识库选择器旁边新增：

```tsx
{/* Top-K 滑块，仅在选择了知识库时显示 */}
{selectedKbId && (
  <div className="flex items-center gap-3">
    <label className="text-sm text-gray-600">检索数量 (Top-K):</label>
    <input
      type="range"
      min={1}
      max={10}
      value={topK}
      onChange={e => setTopK(Number(e.target.value))}
      className="w-32"
    />
    <span className="text-sm font-medium text-gray-900 w-6">{topK}</span>
  </div>
)}
```

需要新增状态：

```typescript
const [topK, setTopK] = useState(3)
```

修改生成请求：

```typescript
const generator = demosApi.generate({
  prompt,
  language,
  frameworkId: selectedFrameworkId || undefined,
  kbId: selectedKbId || undefined,
  topK: selectedKbId ? topK : undefined,
})
```

- [ ] **Step 5: 验证前端编译**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/KbPage.tsx \
        frontend/src/pages/DemoPage.tsx
git commit -m "feat: KbPage embedding model/dim selector + DemoPage top-K slider"
```

---

## 任务依赖关系

```
Task 1 (V8 迁移)
  └─→ Task 2 (模型 + Mapper)
       └─→ Task 3 (EmbeddingService)
            └─→ Task 4 (EmbeddingConfigService + Controller)
                 └─→ Task 5 (KbService 改造)
                      └─→ Task 6 (DemoService + DemoToolProvider)

Task 7 (前端类型 + API) ← 可与 Task 2 并行
  └─→ Task 8 (SettingsPage + EmbeddingSettings) ← 依赖 Task 4 API
  └─→ Task 9 (KbPage + DemoPage) ← 依赖 Task 5/6 API
```

**建议执行顺序**: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9（串行，每个 Task 完成后编译验证）
