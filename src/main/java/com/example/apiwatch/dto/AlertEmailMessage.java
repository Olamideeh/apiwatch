package com.example.apiwatch.dto;

import com.example.apiwatch.enums.AlertType;

import java.time.Instant;
import java.util.UUID;

public record AlertEmailMessage(
        UUID alertId,
        UUID endpointId,
        String endpointName,
        String recipientEmail,
        AlertType type,
        Instant createdAt
) {
}