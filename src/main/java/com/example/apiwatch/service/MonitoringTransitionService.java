package com.example.apiwatch.service;

import com.example.apiwatch.dto.MonitoringTransition;
import com.example.apiwatch.enums.AlertType;
import com.example.apiwatch.enums.EndpointStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MonitoringTransitionService {

    private static final int OUTAGE_THRESHOLD = 3;

    public MonitoringTransition transition(
            int consecutiveFailures,
            boolean outageOpen,
            EndpointStatus checkStatus
    ) {
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException(
                    "Consecutive failures cannot be negative"
            );
        }

        if (checkStatus == null || checkStatus == EndpointStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Check status must be ONLINE, DEGRADED, or OFFLINE"
            );
        }

        return switch (checkStatus) {
            case OFFLINE -> handleFailure(consecutiveFailures, outageOpen);
            case ONLINE -> new MonitoringTransition(
                    0,
                    false,
                    outageOpen
                            ? Optional.of(AlertType.RECOVERY)
                            : Optional.empty()
            );
            case DEGRADED -> new MonitoringTransition(
                    0,
                    outageOpen,
                    Optional.empty()
            );
            case PENDING -> throw new IllegalArgumentException(
                    "PENDING is not a completed check status"
            );
        };
    }

    private MonitoringTransition handleFailure(
            int consecutiveFailures,
            boolean outageOpen
    ) {
        int updatedFailures = consecutiveFailures == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : consecutiveFailures + 1;

        boolean opensOutage =
                !outageOpen && updatedFailures >= OUTAGE_THRESHOLD;

        return new MonitoringTransition(
                updatedFailures,
                outageOpen || opensOutage,
                opensOutage
                        ? Optional.of(AlertType.OUTAGE)
                        : Optional.empty()
        );
    }
}