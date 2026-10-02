package com.example.apiwatch.service;

import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.enums.AlertDeliveryStatus;
import com.example.apiwatch.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertDeliverySchedulerTest {

    private static final Instant NOW =
            Instant.parse("2026-10-02T04:00:00Z");

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertDeliveryCoordinator coordinator;

    @Mock
    private TaskExecutor executor;

    private AlertDeliveryScheduler scheduler;
    private Alert alert;

    @BeforeEach
    void setUp() {
        alert = Alert.builder()
                .id(UUID.randomUUID())
                .build();

        scheduler = new AlertDeliveryScheduler(
                alertRepository,
                coordinator,
                executor,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void emptyScanDoesNotSubmitTasks() {
        stubDueAlerts(List.of());

        scheduler.scanDueAlerts();

        verifyNoInteractions(executor, coordinator);
    }

    @Test
    void submittedTaskCallsDeliveryCoordinator() {
        stubDueAlerts(List.of(alert));

        scheduler.scanDueAlerts();

        ArgumentCaptor<Runnable> captor =
                ArgumentCaptor.forClass(Runnable.class);

        verify(executor).execute(captor.capture());
        verifyNoInteractions(coordinator);

        captor.getValue().run();

        verify(coordinator).deliver(alert.getId());
    }

    @Test
    void repeatedScanDoesNotDuplicateInFlightAlert() {
        stubDueAlerts(List.of(alert));

        scheduler.scanDueAlerts();
        scheduler.scanDueAlerts();

        verify(executor, times(1)).execute(any(Runnable.class));
    }

    @Test
    void completedTaskReleasesLocalMarker() {
        stubDueAlerts(List.of(alert));

        scheduler.scanDueAlerts();

        ArgumentCaptor<Runnable> captor =
                ArgumentCaptor.forClass(Runnable.class);

        verify(executor).execute(captor.capture());
        captor.getValue().run();

        scheduler.scanDueAlerts();

        verify(executor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void deliveryFailureIsContainedAndReleasesMarker() {
        stubDueAlerts(List.of(alert));

        when(coordinator.deliver(alert.getId()))
                .thenThrow(new IllegalStateException(
                        "Database unavailable"
                ));

        scheduler.scanDueAlerts();

        ArgumentCaptor<Runnable> captor =
                ArgumentCaptor.forClass(Runnable.class);

        verify(executor).execute(captor.capture());

        assertDoesNotThrow(() -> captor.getValue().run());

        scheduler.scanDueAlerts();

        verify(executor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void rejectedSubmissionCanBeRetriedOnLaterScan() {
        stubDueAlerts(List.of(alert));

        doThrow(new TaskRejectedException("Workers busy"))
                .doNothing()
                .when(executor)
                .execute(any(Runnable.class));

        assertDoesNotThrow(() -> scheduler.scanDueAlerts());
        assertDoesNotThrow(() -> scheduler.scanDueAlerts());

        verify(executor, times(2)).execute(any(Runnable.class));
        verifyNoInteractions(coordinator);
    }

    private void stubDueAlerts(List<Alert> alerts) {
        when(alertRepository.findDueAlerts(
                List.of(
                        AlertDeliveryStatus.PENDING,
                        AlertDeliveryStatus.FAILED
                ),
                NOW,
                PageRequest.of(0, 50)
        )).thenReturn(alerts);
    }
}