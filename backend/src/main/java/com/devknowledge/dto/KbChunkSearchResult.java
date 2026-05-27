package com.devknowledge.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class KbChunkSearchResult {
    private UUID id;
    private UUID docId;
    private String filename;
    private Integer chunkIndex;
    private String content;
    private double score;
}
