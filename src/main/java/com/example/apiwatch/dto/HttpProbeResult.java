package com.example.apiwatch.dto;

public record HttpProbeResult(
        Integer actualStatusCode,
        long responseTimeMillis,
        String errorMessage
) {
}