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
     * 验证路径安全性，防止路径穿越攻击
     */
    private Path validatePath(UUID userId, String relativePath) {
        Path vaultRoot = getUserVaultPath(userId).normalize();
        Path filePath = vaultRoot.resolve(relativePath).normalize();
        if (!filePath.startsWith(vaultRoot)) {
            throw new SecurityException("非法路径: " + relativePath);
        }
        return filePath;
    }

    /**
     * 写入 wiki 页面文件
     */
    public Mono<Void> writePage(UUID userId, String relativePath, String content) {
        return Mono.fromRunnable(() -> {
            try {
                Path filePath = validatePath(userId, relativePath);
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, content);
                log.debug("写入 wiki 页面: {}", filePath);
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
            Path filePath = validatePath(userId, relativePath);
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
                Path filePath = validatePath(userId, relativePath);
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
            Path filePath = validatePath(userId, relativePath);
            return Files.exists(filePath);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
