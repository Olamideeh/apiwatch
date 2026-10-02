package com.example.apiwatch.dto;

import java.util.UUID;

public record ClaimedEndpointCheck(
        UUID endpointId,
        UUID checkToken,
        String url,
        int timeoutMillis
) {
}