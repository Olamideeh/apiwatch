package com.example.apiwatch.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        boolean active,
        Instant createdAt
) {
}