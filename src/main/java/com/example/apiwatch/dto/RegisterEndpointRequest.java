package com.example.apiwatch.dto;

import jakarta.validation.constraints.*;

public record RegisterEndpointRequest(

        @NotBlank(message = "Endpoint name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "URL is required")
        @Size(max = 2048)
        String url,

        @NotNull(message = "Expected status code is required")
        @Min(100)
        @Max(599)
        Integer expectedStatusCode,

        @NotNull(message = "Check interval is required")
        @Min(60)
        @Max(86400)
        Integer intervalSeconds,

        @NotNull(message = "Timeout is required")
        @Min(100)
        @Max(30000)
        Integer timeoutMillis,

        @NotNull(message = "Response time limit is required")
        @Min(1)
        @Max(30000)
        Integer responseTimeLimitMillis
) {
}