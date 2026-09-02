package com.devknowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 开发者后台通用分页响应，页码从 1 开始。
 */
@Data
@AllArgsConstructor
public class AdminPageResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private long total;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
}
