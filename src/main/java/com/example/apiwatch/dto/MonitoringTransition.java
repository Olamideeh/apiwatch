package com.example.apiwatch.dto;

import com.example.apiwatch.enums.AlertType;

import java.util.Optional;

public record MonitoringTransition(
        int consecutiveFailures,
        boolean outageOpen,
        Optional<AlertType> alertType
) {
}