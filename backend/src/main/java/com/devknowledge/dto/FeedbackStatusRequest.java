package com.devknowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 反馈状态变更请求。仅允许三个状态值。
 */
@Data
public class FeedbackStatusRequest {

    @NotBlank
    @Pattern(regexp = "NEW|IN_PROGRESS|RESOLVED", message = "状态仅允许 NEW / IN_PROGRESS / RESOLVED")
    private String status;
}
