package com.example.apiwatch.dto;

import java.util.UUID;

public record ClaimedAlertEmail(
        UUID deliveryToken,
        AlertEmailMessage message
) {
}