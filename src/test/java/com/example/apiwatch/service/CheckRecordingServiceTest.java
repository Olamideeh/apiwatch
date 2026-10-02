package com.example.apiwatch.service;

import com.example.apiwatch.dto.CheckResultResponse;
import com.example.apiwatch.dto.HttpProbeResult;
import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.entity.CheckResult;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.enums.*;
import com.example.apiwatch.exception.ResourceNotFoundException;
import com.example.apiwatch.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckRecordingServiceTest {

    @Mock
    private MonitoredEndpointRepository endpointRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private AlertRepository alertRepository;

    private CheckRecordingService service;
    private MonitoredEndpoint endpoint;
    private UUID endpointId;
    private UUID checkToken;

    private final Instant checkedAt =
            Instant.parse("2026-10-02T03:00:00Z");

    @BeforeEach
    void setUp() {
        endpointId = UUID.randomUUID();
        checkToken = UUID.randomUUID();

        User owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .active(true)
                .build();

        endpoint = MonitoredEndpoint.builder()
                .id(endpointId)
                .owner(owner)
                .name("Health Check")
                .url("https://example.com/health")
                .nextCheckAt(checkedAt)
                .checkToken(checkToken)
                .checkLeaseUntil(checkedAt.plusSeconds(120))
                .build();

        service = new CheckRecordingService(
                endpointRepository,
                checkResultRepository,
                alertRepository,
                new EndpointStatusClassifier(),
                new MonitoringTransitionService()
        );
    }

    @Test
    void recordsOnlineResultAndSchedulesNextCheck() {
        stubRecording();

        CheckResultResponse response = service.record(
                endpointId,
                checkToken,
                new HttpProbeResult(200, 100, null),
                checkedAt
        ).orElseThrow();

        assertAll(
                () -> assertNotNull(response.id()),
                () -> assertEquals(endpointId, response.endpointId()),
                () -> assertEquals(
                        EndpointStatus.ONLINE, response.status()
                ),
                () -> assertEquals(
                        Integer.valueOf(200), response.actualStatusCode()
                ),
                () -> assertEquals(100L, response.responseTimeMillis()),
                () -> assertEquals(checkedAt, response.checkedAt()),
                () -> assertEquals(
                        EndpointStatus.ONLINE, endpoint.getCurrentStatus()
                ),
                () -> assertEquals(
                        checkedAt, endpoint.getLastCheckedAt()
                ),
                () -> assertEquals(
                        checkedAt.plusSeconds(60), endpoint.getNextCheckAt()
                ),
                () -> assertEquals(0, endpoint.getConsecutiveFailures()),
                () -> assertNull(endpoint.getCheckToken()),
                () -> assertNull(endpoint.getCheckLeaseUntil())
        );

        verify(endpointRepository).findByIdForUpdate(endpointId);
        verify(endpointRepository).save(endpoint);
        verifyNoInteractions(alertRepository);
    }

    @Test
    void thirdFailureCreatesPendingOutageAlert() {
        endpoint.setConsecutiveFailures(2);
        stubRecording();

        service.record(
                endpointId,
                checkToken,
                new HttpProbeResult(500, 100, null),
                checkedAt
        );

        Alert alert = capturedAlert();

        assertAll(
                () -> assertEquals(
                        EndpointStatus.OFFLINE, endpoint.getCurrentStatus()
                ),
                () -> assertEquals(3, endpoint.getConsecutiveFailures()),
                () -> assertTrue(endpoint.isOutageOpen()),
                () -> assertEquals(AlertType.OUTAGE, alert.getType()),
                () -> assertEquals(
                        AlertDeliveryStatus.PENDING,
                        alert.getDeliveryStatus()
                ),
                () -> assertEquals(
                        "owner@example.com", alert.getRecipientEmail()
                ),
                () -> assertSame(endpoint, alert.getEndpoint()),
                () -> assertEquals(0, alert.getAttemptCount())
        );
    }

    @Test
    void continuingOutageDoesNotCreateAnotherAlert() {
        endpoint.setConsecutiveFailures(3);
        endpoint.setOutageOpen(true);
        stubRecording();

        service.record(
                endpointId,
                checkToken,
                new HttpProbeResult(null, 5000, "HTTP check failed"),
                checkedAt
        );

        assertEquals(4, endpoint.getConsecutiveFailures());
        assertTrue(endpoint.isOutageOpen());

        ArgumentCaptor<CheckResult> captor =
                ArgumentCaptor.forClass(CheckResult.class);

        verify(checkResultRepository).save(captor.capture());

        assertNull(captor.getValue().getActualStatusCode());
        assertEquals(
                "HTTP check failed", captor.getValue().getErrorMessage()
        );

        verifyNoInteractions(alertRepository);
    }

    @Test
    void onlineClosesOutageAndCreatesRecoveryAlert() {
        endpoint.setConsecutiveFailures(4);
        endpoint.setOutageOpen(true);
        stubRecording();

        service.record(
                endpointId,
                checkToken,
                new HttpProbeResult(200, 100, null),
                checkedAt
        );

        assertFalse(endpoint.isOutageOpen());
        assertEquals(0, endpoint.getConsecutiveFailures());
        assertEquals(AlertType.RECOVERY, capturedAlert().getType());
    }

    @Test
    void degradedKeepsOutageOpenWithoutRecoveryAlert() {
        endpoint.setConsecutiveFailures(4);
        endpoint.setOutageOpen(true);
        stubRecording();

        service.record(
                endpointId,
                checkToken,
                new HttpProbeResult(200, 1500, null),
                checkedAt
        );

        assertEquals(
                EndpointStatus.DEGRADED, endpoint.getCurrentStatus()
        );
        assertEquals(0, endpoint.getConsecutiveFailures());
        assertTrue(endpoint.isOutageOpen());

        verifyNoInteractions(alertRepository);
    }

    @Test
    void pausedEndpointIsSkippedWithoutWrites() {
        endpoint.setPaused(true);
        stubEndpoint();

        assertTrue(service.record(
                endpointId,
                checkToken,
                new HttpProbeResult(200, 100, null),
                checkedAt
        ).isEmpty());

        verifyNoInteractions(checkResultRepository, alertRepository);
        verify(endpointRepository, never())
                .save(any(MonitoredEndpoint.class));
    }

    @Test
    void inactiveOwnerIsSkippedWithoutWrites() {
        endpoint.getOwner().setActive(false);
        stubEndpoint();

        assertTrue(service.record(
                endpointId,
                checkToken,
                new HttpProbeResult(200, 100, null),
                checkedAt
        ).isEmpty());

        verifyNoInteractions(checkResultRepository, alertRepository);
        verify(endpointRepository, never())
                .save(any(MonitoredEndpoint.class));
    }

    @Test
    void unavailableEndpointReturnsNotFound() {
        when(endpointRepository.findByIdForUpdate(endpointId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.record(
                        endpointId,
                        checkToken,
                        new HttpProbeResult(200, 100, null),
                        checkedAt
                )
        );

        verifyNoInteractions(checkResultRepository, alertRepository);
    }

    @Test
    void invalidArgumentsAreRejectedBeforeDatabaseAccess() {
        HttpProbeResult valid = new HttpProbeResult(200, 100, null);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(
                                null, checkToken, valid, checkedAt
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(
                                endpointId, checkToken, null, checkedAt
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(
                                endpointId, checkToken, valid, null
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(
                                endpointId,
                                checkToken,
                                new HttpProbeResult(200, -1, null),
                                checkedAt
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(
                                endpointId,
                                checkToken,
                                new HttpProbeResult(600, 100, null),
                                checkedAt
                        )
                )
        );

        verifyNoInteractions(
                endpointRepository,
                checkResultRepository,
                alertRepository
        );
    }

    @Test
    void staleClaimTokenIsRejectedWithoutWrites() {
        stubEndpoint();

        assertTrue(service.record(
                endpointId,
                UUID.randomUUID(),
                new HttpProbeResult(200, 100, null),
                checkedAt
        ).isEmpty());

        verifyNoInteractions(checkResultRepository, alertRepository);
        verify(endpointRepository, never())
                .save(any(MonitoredEndpoint.class));

        assertEquals(checkToken, endpoint.getCheckToken());
    }

    @Test
    void successfulRecordingConsumesClaimAndRejectsReplay() {
        stubRecording();

        HttpProbeResult probe = new HttpProbeResult(200, 100, null);

        assertTrue(service.record(
                endpointId, checkToken, probe, checkedAt
        ).isPresent());

        assertNull(endpoint.getCheckToken());
        assertNull(endpoint.getCheckLeaseUntil());

        assertTrue(service.record(
                endpointId, checkToken, probe, checkedAt
        ).isEmpty());

        verify(checkResultRepository, times(1))
                .save(any(CheckResult.class));

        verify(endpointRepository, times(1)).save(endpoint);
    }

    @Test
    void missingClaimTokenIsRejectedBeforeDatabaseAccess() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.record(
                        endpointId,
                        null,
                        new HttpProbeResult(200, 100, null),
                        checkedAt
                )
        );

        verifyNoInteractions(
                endpointRepository,
                checkResultRepository,
                alertRepository
        );
    }

    private void stubEndpoint() {
        when(endpointRepository.findByIdForUpdate(endpointId))
                .thenReturn(Optional.of(endpoint));
    }

    private void stubRecording() {
        stubEndpoint();

        when(checkResultRepository.save(any(CheckResult.class)))
                .thenAnswer(invocation -> {
                    CheckResult result = invocation.getArgument(0);

                    return CheckResult.builder()
                            .id(UUID.randomUUID())
                            .endpoint(result.getEndpoint())
                            .status(result.getStatus())
                            .actualStatusCode(result.getActualStatusCode())
                            .responseTimeMillis(result.getResponseTimeMillis())
                            .errorMessage(result.getErrorMessage())
                            .checkedAt(result.getCheckedAt())
                            .build();
                });
    }

    private Alert capturedAlert() {
        ArgumentCaptor<Alert> captor =
                ArgumentCaptor.forClass(Alert.class);

        verify(alertRepository).save(captor.capture());

        return captor.getValue();
    }
}