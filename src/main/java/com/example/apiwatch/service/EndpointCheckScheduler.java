package com.example.apiwatch.service;

import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
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
        name = "apiwatch.monitoring.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EndpointCheckScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(EndpointCheckScheduler.class);

    private final MonitoredEndpointRepository endpointRepository;
    private final EndpointCheckCoordinator coordinator;
    private final TaskExecutor executor;
    private final Clock clock;

    private final Set<UUID> inFlight =
            ConcurrentHashMap.newKeySet();

    public EndpointCheckScheduler(
            MonitoredEndpointRepository endpointRepository,
            EndpointCheckCoordinator coordinator,
            @Qualifier("monitoringExecutor") TaskExecutor executor,
            Clock clock
    ) {
        this.endpointRepository = endpointRepository;
        this.coordinator = coordinator;
        this.executor = executor;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${apiwatch.monitoring.scan-delay-millis:5000}",
            initialDelayString = "${apiwatch.monitoring.initial-delay-millis:10000}"
    )
    public void scanDueEndpoints() {
        List<MonitoredEndpoint> dueEndpoints;

        try {
            dueEndpoints = endpointRepository.findDueEndpoints(
                    clock.instant(),
                    PageRequest.of(0, 50)
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Endpoint scan failed: {}",
                    exception.getClass().getSimpleName()
            );
            return;
        }

        for (MonitoredEndpoint endpoint : dueEndpoints) {
            UUID endpointId = endpoint.getId();

            if (!inFlight.add(endpointId)) {
                continue;
            }

            try {
                executor.execute(() -> runCheck(endpointId));
            } catch (TaskRejectedException exception) {
                inFlight.remove(endpointId);
                log.debug(
                        "Check deferred because workers are busy: {}",
                        endpointId
                );
            } catch (RuntimeException exception) {
                inFlight.remove(endpointId);
                log.error(
                        "Check submission failed for {}: {}",
                        endpointId,
                        exception.getClass().getSimpleName()
                );
            }
        }
    }

    private void runCheck(UUID endpointId) {
        try {
            coordinator.check(endpointId);
        } catch (RuntimeException exception) {
            log.error(
                    "Endpoint check failed for {}: {}",
                    endpointId,
                    exception.getClass().getSimpleName()
            );
        } finally {
            inFlight.remove(endpointId);
        }
    }
}