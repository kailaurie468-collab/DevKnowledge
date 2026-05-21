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

    /**
     * 从文件字节流中提取纯文本内容
     */
    public String parse(String filename, byte[] content) throws IOException {
        String ext = getExtension(filename).toLowerCase();
        return switch (ext) {
            case "txt", "md", "markdown" -> parseText(content);
            case "pdf" -> parsePdf(content);
            case "docx" -> parseDocx(content);
            default -> throw new IOException("不支持的文件格式: " + ext);
        };
    }

    private String parseText(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
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
