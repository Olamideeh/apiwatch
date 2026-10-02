package com.example.apiwatch.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UptimeResponse(
        UUID endpointId,
        Instant from,
        Instant to,
        long totalChecks,
        long availableChecks,
        long offlineChecks,
        BigDecimal uptimePercentage
) {
}