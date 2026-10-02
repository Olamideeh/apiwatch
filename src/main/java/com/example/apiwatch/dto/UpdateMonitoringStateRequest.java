package com.example.apiwatch.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMonitoringStateRequest(

        @NotNull(message = "Paused state is required")
        Boolean paused
) {
}