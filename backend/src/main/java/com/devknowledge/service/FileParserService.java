package com.devknowledge.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Service
public class FileParserService {

    private static final Logger log = LoggerFactory.getLogger(FileParserService.class);

    /** 大文件阈值：1MB，超过此大小的文本文件使用流式解析 */
    private static final int LARGE_FILE_THRESHOLD = 1024 * 1024;

    /**
     * 从文件字节流中提取纯文本内容
     * 小文件（< 1MB）直接转换，大文件（>= 1MB）使用流式解析
     */
    public String parse(String filename, byte[] content) throws IOException {
        String ext = getExtension(filename).toLowerCase();
        return switch (ext) {
            case "txt", "md", "markdown" -> {
                if (content.length < LARGE_FILE_THRESHOLD) {
                    //用 yield 来指定该分支的返回值。
                    yield parseText(content);
                } else {
                    log.info("大文件流式解析: {} ({}KB)", filename, content.length / 1024);
                    yield parseTextStreaming(content);
                }
            }
            case "pdf" -> parsePdf(content);
            case "docx" -> parseDocx(content);
            default -> throw new IOException("不支持的文件格式: " + ext);
        };
    }

    /**
     * 从 InputStream 解析文本（用于流式上传）
     * 自动根据流大小选择直接解析或流式解析
     */
    public String parseFromStream(String filename, InputStream inputStream) throws IOException {
        String ext = getExtension(filename).toLowerCase();
        return switch (ext) {
            case "txt", "md", "markdown" -> parseTextFromStream(inputStream);
            case "pdf" -> parsePdf(inputStream.readAllBytes());
            case "docx" -> parseDocx(inputStream.readAllBytes());
            default -> throw new IOException("不支持的文件格式: " + ext);
        };
    }

    /**
     * 直接将 byte[] 转换为 String（适用于小文件 < 1MB）
     */
    private String parseText(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    /**
     * 流式解析大文本文件（>= 1MB）
     * 使用 BufferedReader 逐行读取，避免一次性加载全部内容到堆内存
     * 通过 ByteArrayOutputStream 分块拼接，减少 String 对象创建
     */
    private String parseTextStreaming(byte[] content) throws IOException {
        try (InputStream is = new ByteArrayInputStream(content);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(reader, 65536)) {

            StringBuilder sb = new StringBuilder(content.length);
            char[] buffer = new char[65536];
            int charsRead;
            while ((charsRead = br.read(buffer)) != -1) {
                sb.append(buffer, 0, charsRead);
            }
            return sb.toString();
        }
    }

    /**
     * 从 InputStream 流式解析文本内容
     * 适用于大文件上传场景，边读边处理
     */
    private String parseTextFromStream(InputStream inputStream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(reader, 65536)) {

            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[65536];
            int charsRead;
            while ((charsRead = br.read(buffer)) != -1) {
                sb.append(buffer, 0, charsRead);
            }
            return sb.toString();
        }
    }

    private String parsePdf(byte[] content) throws IOException {
        try (PDDocument doc = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String parseDocx(byte[] content) throws IOException {
        try (InputStream is = new ByteArrayInputStream(content);
             XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            return sb.toString().trim();
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
