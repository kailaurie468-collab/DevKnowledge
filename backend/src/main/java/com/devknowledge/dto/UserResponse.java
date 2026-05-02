package com.devknowledge.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UserResponse {

    private UUID id;
    private String email;
    private String displayName;
}
