package com.devknowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

/**
 * 原始文件存储服务
 * 将上传的原始文件持久化到磁盘，用于文档解析重试
 */
@Slf4j
@Service
public class RawFileStorageService {

    @Value("${kb.raw-file.storage-path:raw_files}")
    private String storageBasePath;

    /**
     * 存储原始文件到磁盘
     * 目录结构：raw_files/{kbId}/{docId}/{filename}
     */
    public void store(UUID kbId, UUID docId, String filename, byte[] content) throws IOException {
        Path dir = Path.of(storageBasePath, kbId.toString(), docId.toString());
        Files.createDirectories(dir);
        Path filePath = dir.resolve(filename);
        Files.write(filePath, content);
        log.info("存储原始文件: {} ({} bytes)", filePath, content.length);
    }

    /**
     * 读取原始文件
     */
    public byte[] read(UUID kbId, UUID docId, String filename) throws IOException {
        Path filePath = Path.of(storageBasePath, kbId.toString(), docId.toString(), filename);
        if (!Files.exists(filePath)) {
            throw new IOException("原始文件不存在: " + filePath);
        }
        return Files.readAllBytes(filePath);
    }

    /**
     * 删除原始文件目录
     * 删除后如果父目录（kbId目录）为空，也一并删除
     */
    public void delete(UUID kbId, UUID docId) {
        Path docDir = Path.of(storageBasePath, kbId.toString(), docId.toString());
        Path kbDir = Path.of(storageBasePath, kbId.toString());
        try {
            if (Files.exists(docDir)) {
                // 递归删除 docId 目录下所有文件
                try (var stream = Files.walk(docDir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                            });
                }
                log.info("删除原始文件目录: {}", docDir);

                // 如果 kbId 目录为空，也删除
                if (Files.exists(kbDir) && isDirectoryEmpty(kbDir)) {
                    Files.delete(kbDir);
                    log.info("删除空的知识库文件目录: {}", kbDir);
                }
            }
        } catch (IOException e) {
            log.warn("删除原始文件目录失败: {}", e.getMessage());
        }
    }

    /**
     * 检查目录是否为空
     */
    private boolean isDirectoryEmpty(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.findFirst().isEmpty();
        }
    }

    /**
     * 检查原始文件是否存在
     */
    public boolean exists(UUID kbId, UUID docId, String filename) {
        Path filePath = Path.of(storageBasePath, kbId.toString(), docId.toString(), filename);
        return Files.exists(filePath);
    }
}
