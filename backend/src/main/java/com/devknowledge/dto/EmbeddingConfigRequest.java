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
