package com.devknowledge.dto;

import lombok.Data;

import java.util.Map;

/**
 * 用户行为记录请求体
 */
@Data
public class ActivityRequest {

    /** 行为类型：demo_generate / kb_search / skill_extract 等 */
    private String type;

    /** 使用的框架 */
    private String framework;

    /** 关键词列表 */
    private String[] keywords;

    /** 编程语言 */
    private String language;

    /** 结果数量 */
    private Integer resultCount;

    /** 额外元数据 */
    private Map<String, Object> metadata;
}
