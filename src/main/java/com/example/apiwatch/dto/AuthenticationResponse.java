package com.example.apiwatch.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthenticationResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UUID userId,
        String email
) {
}