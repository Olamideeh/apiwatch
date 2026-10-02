package com.example.apiwatch.dto;

import java.time.Instant;

public record GeneratedJwtToken(
        String token,
        Instant expiresAt
) {
}
