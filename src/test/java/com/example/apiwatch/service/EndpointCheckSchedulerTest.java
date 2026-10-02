package com.example.apiwatch.service;

import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointCheckSchedulerTest {

    private static final Instant NOW =
            Instant.parse("2026-10-02T03:00:00Z");

    @Mock
    private MonitoredEndpointRepository endpointRepository;

    @Mock
    private EndpointCheckCoordinator coordinator;

    @Mock
    private TaskExecutor executor;

    private EndpointCheckScheduler scheduler;
    private MonitoredEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = MonitoredEndpoint.builder()
                .id(UUID.randomUUID())
                .build();

        scheduler = new EndpointCheckScheduler(
                endpointRepository,
                coordinator,
                executor,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void emptyScanDoesNotSubmitTasks() {
        stubDueEndpoints(List.of());

        scheduler.scanDueEndpoints();

        verifyNoInteractions(executor, coordinator);
    }

    @Test
    void submitsTaskThatRunsCoordinator() {
        stubDueEndpoints(List.of(endpoint));

        scheduler.scanDueEndpoints();

        ArgumentCaptor<Runnable> captor =
                ArgumentCaptor.forClass(Runnable.class);

        verify(executor).execute(captor.capture());
        verifyNoInteractions(coordinator);

        captor.getValue().run();

        verify(coordinator).check(endpoint.getId());
    }

    @Test
    void repeatedScanDoesNotDuplicateInFlightEndpoint() {
        stubDueEndpoints(List.of(endpoint));

        scheduler.scanDueEndpoints();
        scheduler.scanDueEndpoints();

        verify(executor, times(1)).execute(any(Runnable.class));
    }

    @Test
    void completedTaskCanBeSubmittedOnLaterScan() {
        stubDueEndpoints(List.of(endpoint));

        scheduler.scanDueEndpoints();

        ArgumentCaptor<Runnable> captor =
                ArgumentCaptor.forClass(Runnable.class);

        verify(executor).execute(captor.capture());
        captor.getValue().run();

        scheduler.scanDueEndpoints();

        verify(executor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void taskFailureIsContainedAndReleasesInFlightMarker() {
        stubDueEndpoints(List.of(endpoint));

        when(coordinator.check(endpoint.getId()))
                .thenThrow(new IllegalStateException(
                        "Database unavailable"
                ));

        scheduler.scanDueEndpoints();

        ArgumentCaptor<Runnable> captor =
                ArgumentCaptor.forClass(Runnable.class);

        verify(executor).execute(captor.capture());

        assertDoesNotThrow(() -> captor.getValue().run());

        scheduler.scanDueEndpoints();

        verify(executor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void rejectedTaskCanBeSubmittedOnLaterScan() {
        stubDueEndpoints(List.of(endpoint));

        doThrow(new TaskRejectedException("Workers busy"))
                .doNothing()
                .when(executor)
                .execute(any(Runnable.class));

        assertDoesNotThrow(() -> scheduler.scanDueEndpoints());
        assertDoesNotThrow(() -> scheduler.scanDueEndpoints());

        verify(executor, times(2)).execute(any(Runnable.class));
        verifyNoInteractions(coordinator);
    }

    private void stubDueEndpoints(List<MonitoredEndpoint> endpoints) {
        when(endpointRepository.findDueEndpoints(
                NOW,
                PageRequest.of(0, 50)
        )).thenReturn(endpoints);
    }
}