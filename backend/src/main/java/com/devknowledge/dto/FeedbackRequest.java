package com.devknowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户意见反馈请求。
 */
@Data
public class FeedbackRequest {

    @NotBlank
    @Size(max = 64)
    private String feedbackType;

    @NotBlank
    @Size(max = 5000)
    private String content;

    @Size(max = 255)
    private String contact;

    @Size(max = 512)
    private String page;

    @Size(max = 64)
    private String requestId;
}
