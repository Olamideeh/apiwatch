package com.example.apiwatch.dto;

import com.example.apiwatch.enums.EndpointStatus;

import java.time.Instant;
import java.util.UUID;

public record EndpointResponse(
        UUID id,
        String name,
        String url,
        int expectedStatusCode,
        int intervalSeconds,
        int timeoutMillis,
        int responseTimeLimitMillis,
        boolean paused,
        EndpointStatus currentStatus,
        int consecutiveFailures,
        Instant nextCheckAt,
        Instant lastCheckedAt,
        Instant createdAt
) {
}