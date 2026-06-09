package com.devknowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RawFileStorageService - 原始文件存储")
class RawFileStorageServiceTest {

    private RawFileStorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        storageService = new RawFileStorageService();
        // 通过反射设置临时目录路径
        Field field = RawFileStorageService.class.getDeclaredField("storageBasePath");
        field.setAccessible(true);
        field.set(storageService, tempDir.toString());
    }

    private final UUID kbId = UUID.randomUUID();
    private final UUID docId = UUID.randomUUID();

    // ==================== 存储与读取 ====================

    @Nested
    @DisplayName("存储与读取")
    class StoreAndRead {

        @Test
        @DisplayName("存储文件后能正确读取")
        void storeAndRead() throws IOException {
            byte[] content = "测试文件内容".getBytes();
            storageService.store(kbId, docId, "test.md", content);

            byte[] read = storageService.read(kbId, docId, "test.md");
            assertThat(read).isEqualTo(content);
        }

        @Test
        @DisplayName("存储英文内容")
        void storeEnglishContent() throws IOException {
            byte[] content = "Hello, World!".getBytes();
            storageService.store(kbId, docId, "hello.txt", content);

            byte[] read = storageService.read(kbId, docId, "hello.txt");
            assertThat(new String(read)).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("创建正确的目录结构 {kbId}/{docId}/{filename}")
        void correctDirectoryStructure() throws IOException {
            storageService.store(kbId, docId, "file.md", "content".getBytes());

            Path expected = tempDir.resolve(kbId.toString())
                    .resolve(docId.toString())
                    .resolve("file.md");
            assertThat(Files.exists(expected)).isTrue();
        }
    }

    // ==================== 存在性检查 ====================

    @Nested
    @DisplayName("存在性检查")
    class ExistenceCheck {

        @Test
        @DisplayName("已存储文件 exists 返回 true")
        void existsAfterStore() throws IOException {
            storageService.store(kbId, docId, "test.md", "content".getBytes());
            assertThat(storageService.exists(kbId, docId, "test.md")).isTrue();
        }

        @Test
        @DisplayName("未存储文件 exists 返回 false")
        void notExists() {
            assertThat(storageService.exists(kbId, docId, "nonexistent.md")).isFalse();
        }
    }

    // ==================== 删除 ====================

    @Nested
    @DisplayName("删除")
    class Deletion {

        @Test
        @DisplayName("删除后文件不存在")
        void deleteRemovesFile() throws IOException {
            storageService.store(kbId, docId, "test.md", "content".getBytes());
            assertThat(storageService.exists(kbId, docId, "test.md")).isTrue();

            storageService.delete(kbId, docId);
            assertThat(storageService.exists(kbId, docId, "test.md")).isFalse();
        }

        @Test
        @DisplayName("删除不存在的目录不抛异常")
        void deleteNonexistent() {
            // 不应抛出异常
            storageService.delete(UUID.randomUUID(), UUID.randomUUID());
        }

        @Test
        @DisplayName("删除后目录也被清理")
        void deleteRemovesDirectory() throws IOException {
            storageService.store(kbId, docId, "test.md", "content".getBytes());
            storageService.delete(kbId, docId);

            Path dir = tempDir.resolve(kbId.toString()).resolve(docId.toString());
            assertThat(Files.exists(dir)).isFalse();
        }
    }

    // ==================== 异常处理 ====================

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("读取不存在的文件抛出 IOException")
        void readNonexistent() {
            assertThatThrownBy(() -> storageService.read(kbId, docId, "missing.md"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("原始文件不存在");
        }
    }
}
