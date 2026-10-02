package com.example.apiwatch.service;

import com.example.apiwatch.dto.AlertEmailMessage;
import com.example.apiwatch.dto.ClaimedAlertEmail;
import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.enums.AlertDeliveryStatus;
import com.example.apiwatch.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertDeliveryStateService {

    private final AlertRepository alertRepository;
    private final Clock clock;

    @Transactional
    public Optional<ClaimedAlertEmail> claim(UUID alertId) {
        requireAlertId(alertId);

        Optional<Alert> found =
                alertRepository.findByIdForUpdate(alertId);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        Alert alert = found.get();
        Instant now = clock.instant();

        if (alert.getDeliveryStatus() == AlertDeliveryStatus.SENT
                || alert.getAttemptCount() >= 3
                || alert.getNextAttemptAt().isAfter(now)) {
            return Optional.empty();
        }

        Instant leaseUntil = alert.getDeliveryLeaseUntil();

        if (leaseUntil != null && leaseUntil.isAfter(now)) {
            return Optional.empty();
        }

        UUID token = UUID.randomUUID();

        alert.setDeliveryToken(token);
        alert.setDeliveryLeaseUntil(now.plusSeconds(120));
        alert.setAttemptCount(alert.getAttemptCount() + 1);

        alertRepository.save(alert);

        AlertEmailMessage message = new AlertEmailMessage(
                alert.getId(),
                alert.getEndpoint().getId(),
                alert.getEndpoint().getName(),
                alert.getRecipientEmail(),
                alert.getType(),
                alert.getCreatedAt()
        );

        return Optional.of(new ClaimedAlertEmail(token, message));
    }

    @Transactional
    public boolean complete(
            UUID alertId,
            UUID deliveryToken,
            boolean successful,
            String errorMessage
    ) {
        requireAlertId(alertId);

        if (deliveryToken == null) {
            throw new IllegalArgumentException(
                    "Delivery token is required"
            );
        }

        Optional<Alert> found =
                alertRepository.findByIdForUpdate(alertId);

        if (found.isEmpty()) {
            return false;
        }

        Alert alert = found.get();

        if (!deliveryToken.equals(alert.getDeliveryToken())) {
            return false;
        }

        Instant now = clock.instant();

        if (successful) {
            alert.setDeliveryStatus(AlertDeliveryStatus.SENT);
            alert.setSentAt(now);
            alert.setLastError(null);
        } else {
            alert.setDeliveryStatus(AlertDeliveryStatus.FAILED);
            alert.setSentAt(null);
            alert.setLastError(limitError(errorMessage));
            alert.setNextAttemptAt(now.plusSeconds(60));
        }

        alert.setDeliveryToken(null);
        alert.setDeliveryLeaseUntil(null);

        alertRepository.save(alert);

        return true;
    }

    private void requireAlertId(UUID alertId) {
        if (alertId == null) {
            throw new IllegalArgumentException(
                    "Alert ID is required"
            );
        }
    }

    private String limitError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Email delivery failed";
        }

        return errorMessage.length() <= 2000
                ? errorMessage
                : errorMessage.substring(0, 2000);
    }
}