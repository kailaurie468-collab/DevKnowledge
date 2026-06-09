package com.devknowledge.service;

import com.devknowledge.mapper.KbChunkMapper;
import com.devknowledge.mapper.KbDocumentMapper;
import com.devknowledge.mapper.KnowledgeBaseMapper;
import com.devknowledge.model.KbDocument;
import com.devknowledge.model.KnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KbService - 文档重试逻辑")
class KbServiceRetryTest {

    @Mock
    private KnowledgeBaseMapper kbMapper;
    @Mock
    private KbDocumentMapper docMapper;
    @Mock
    private FileParserService fileParserService;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private EmbeddingConfigService embeddingConfigService;
    @Mock
    private EmbeddingUsageService embeddingUsageService;
    @Mock
    private KbChunkMapper chunkMapper;
    @Mock
    private MarkdownChunker markdownChunker;
    @Mock
    private RawFileStorageService rawFileStorageService;

    @InjectMocks
    private KbService kbService;

    private UUID userId;
    private UUID kbId;
    private UUID docId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        kbId = UUID.randomUUID();
        docId = UUID.randomUUID();
    }

    // ==================== 重试成功场景 ====================

    @Nested
    @DisplayName("重试成功")
    class RetrySuccess {

        @Test
        @DisplayName("error 状态文档可触发重试，状态变为 processing")
        void retryErrorDocument() throws Exception {
            // 准备数据
            KbDocument doc = createErrorDoc();
            KnowledgeBase kb = createKb();

            when(docMapper.selectById(docId)).thenReturn(doc);
            when(kbMapper.selectById(kbId)).thenReturn(kb);
            when(rawFileStorageService.read(kbId, docId, "test.md"))
                    .thenReturn("文件内容".getBytes());

            // 执行
            KbDocument result = kbService.retryDocument(docId, userId).block();

            // 验证
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("processing");
            assertThat(result.getErrorMessage()).isNull();
            verify(docMapper, atLeastOnce()).updateById(any(KbDocument.class));
        }
    }

    // ==================== 异常场景 ====================

    @Nested
    @DisplayName("异常场景")
    class RetryExceptions {

        @Test
        @DisplayName("非 error 状态文档不能重试")
        void retryNonErrorDocument() {
            KbDocument doc = new KbDocument();
            doc.setId(docId);
            doc.setKbId(kbId);
            doc.setStatus("ready");

            KnowledgeBase kb = createKb();

            when(docMapper.selectById(docId)).thenReturn(doc);
            when(kbMapper.selectById(kbId)).thenReturn(kb);

            assertThatThrownBy(() -> kbService.retryDocument(docId, userId).block())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("文档状态不是错误");
        }

        @Test
        @DisplayName("文档不存在时重试失败")
        void retryNonexistentDocument() {
            when(docMapper.selectById(docId)).thenReturn(null);

            assertThatThrownBy(() -> kbService.retryDocument(docId, userId).block())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("文档不存在");
        }

        @Test
        @DisplayName("userId 不匹配时重试失败")
        void retryWithWrongUserId() {
            KbDocument doc = createErrorDoc();
            KnowledgeBase kb = createKb();

            when(docMapper.selectById(docId)).thenReturn(doc);
            when(kbMapper.selectById(kbId)).thenReturn(kb);

            UUID wrongUser = UUID.randomUUID();
            assertThatThrownBy(() -> kbService.retryDocument(docId, wrongUser).block())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("无权操作");
        }

        @Test
        @DisplayName("原始文件不存在时重试失败")
        void retryWithoutRawFile() throws Exception {
            KbDocument doc = createErrorDoc();
            KnowledgeBase kb = createKb();

            when(docMapper.selectById(docId)).thenReturn(doc);
            when(kbMapper.selectById(kbId)).thenReturn(kb);
            when(rawFileStorageService.read(kbId, docId, "test.md"))
                    .thenThrow(new java.io.IOException("文件不存在"));

            assertThatThrownBy(() -> kbService.retryDocument(docId, userId).block())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("原始文件不存在");
        }
    }

    // ==================== 辅助方法 ====================

    private KbDocument createErrorDoc() {
        KbDocument doc = new KbDocument();
        doc.setId(docId);
        doc.setKbId(kbId);
        doc.setFilename("test.md");
        doc.setFileType("md");
        doc.setFileSize(1024L);
        doc.setStatus("error");
        doc.setErrorMessage("解析失败");
        return doc;
    }

    private KnowledgeBase createKb() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(kbId);
        kb.setUserId(userId);
        kb.setName("测试知识库");
        kb.setEmbeddingModel("text-embedding-3-small");
        return kb;
    }
}
