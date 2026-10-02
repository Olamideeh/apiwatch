package com.example.apiwatch.dto;

import com.example.apiwatch.enums.EndpointStatus;

import java.time.Instant;
import java.util.UUID;

public record CheckResultResponse(
        UUID id,
        UUID endpointId,
        EndpointStatus status,
        Integer actualStatusCode,
        long responseTimeMillis,
        String errorMessage,
        Instant checkedAt
) {
}