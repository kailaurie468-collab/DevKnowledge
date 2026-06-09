package com.devknowledge.dto;

import lombok.Data;

@Data
public class KbCreateRequest {
    private String name;
    private String description;
    /** Embedding 模型（text-embedding-3-small / large / ada-002） */
    private String embeddingModel;
}
