package com.devknowledge.dto;

import lombok.Data;

/**
 * Skill 导出响应体
 */
@Data
public class SkillExportResponse {

    /** 导出的 Markdown 内容 */
    private String content;

    public SkillExportResponse() {}

    public SkillExportResponse(String content) {
        this.content = content;
    }
}
