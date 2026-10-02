package com.example.apiwatch.service;

import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.enums.AlertDeliveryStatus;
import com.example.apiwatch.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(
        name = "apiwatch.alerts.enabled",
        havingValue = "true"
)
public class AlertDeliveryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(AlertDeliveryScheduler.class);

    private final AlertRepository alertRepository;
    private final AlertDeliveryCoordinator coordinator;
    private final TaskExecutor executor;
    private final Clock clock;

    private final Set<UUID> inFlight =
            ConcurrentHashMap.newKeySet();

    public AlertDeliveryScheduler(
            AlertRepository alertRepository,
            AlertDeliveryCoordinator coordinator,
            @Qualifier("alertExecutor") TaskExecutor executor,
            Clock clock
    ) {
        this.alertRepository = alertRepository;
        this.coordinator = coordinator;
        this.executor = executor;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${apiwatch.alerts.scan-delay-millis:5000}",
            initialDelayString = "${apiwatch.alerts.initial-delay-millis:10000}"
    )
    public void scanDueAlerts() {
        List<Alert> alerts;

        try {
            alerts = alertRepository.findDueAlerts(
                    List.of(
                            AlertDeliveryStatus.PENDING,
                            AlertDeliveryStatus.FAILED
                    ),
                    clock.instant(),
                    PageRequest.of(0, 50)
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Alert scan failed: {}",
                    exception.getClass().getSimpleName()
            );
            return;
        }

        for (Alert alert : alerts) {
            UUID alertId = alert.getId();

            if (!inFlight.add(alertId)) {
                continue;
            }

            try {
                executor.execute(() -> deliver(alertId));
            } catch (TaskRejectedException exception) {
                inFlight.remove(alertId);
                log.debug("Alert delivery deferred: {}", alertId);
            } catch (RuntimeException exception) {
                inFlight.remove(alertId);
                log.error(
                        "Alert submission failed for {}: {}",
                        alertId,
                        exception.getClass().getSimpleName()
                );
            }
        }
    }

    private void deliver(UUID alertId) {
        try {
            coordinator.deliver(alertId);
        } catch (RuntimeException exception) {
            log.error(
                    "Alert delivery failed for {}: {}",
                    alertId,
                    exception.getClass().getSimpleName()
            );
        } finally {
            inFlight.remove(alertId);
        }
    }
}