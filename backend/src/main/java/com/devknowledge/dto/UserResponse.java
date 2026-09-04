package com.devknowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
public class UserResponse {

    private UUID id;
    private String email;
    private String displayName;

    /** 是否管理员（邮箱白名单判定，前端用它决定是否渲染后台入口） */
    @JsonProperty("isAdmin")
    private boolean isAdmin;
}
