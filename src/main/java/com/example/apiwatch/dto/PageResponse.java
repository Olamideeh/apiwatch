package com.example.apiwatch.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public PageResponse {
        content = List.copyOf(content);
    }
}