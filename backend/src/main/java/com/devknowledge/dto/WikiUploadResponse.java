package com.devknowledge.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class WikiUploadResponse {
    private UUID docId;
    private String filename;
    private String status;
    private String message;
}
