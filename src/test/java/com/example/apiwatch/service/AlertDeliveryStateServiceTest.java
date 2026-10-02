package com.example.apiwatch.service;

import com.example.apiwatch.dto.ClaimedAlertEmail;
import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.enums.AlertDeliveryStatus;
import com.example.apiwatch.enums.AlertType;
import com.example.apiwatch.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertDeliveryStateServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-10-02T04:00:00Z");

    @Mock
    private AlertRepository alertRepository;

    private AlertDeliveryStateService service;
    private Alert alert;
    private UUID alertId;
    private UUID deliveryToken;

    @BeforeEach
    void setUp() {
        alertId = UUID.randomUUID();
        deliveryToken = UUID.randomUUID();

        MonitoredEndpoint endpoint = MonitoredEndpoint.builder()
                .id(UUID.randomUUID())
                .name("Health Check")
                .build();

        alert = Alert.builder()
                .id(alertId)
                .endpoint(endpoint)
                .type(AlertType.OUTAGE)
                .recipientEmail("owner@example.com")
                .createdAt(NOW.minusSeconds(60))
                .nextAttemptAt(NOW)
                .build();

        service = new AlertDeliveryStateService(
                alertRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void claimsDueAlertAndCountsAttemptBeforeSending() {
        stubAlert();

        ClaimedAlertEmail claim = service.claim(alertId).orElseThrow();

        assertAll(
                () -> assertNotNull(claim.deliveryToken()),
                () -> assertEquals(
                        claim.deliveryToken(), alert.getDeliveryToken()
                ),
                () -> assertEquals(
                        NOW.plusSeconds(120),
                        alert.getDeliveryLeaseUntil()
                ),
                () -> assertEquals(1, alert.getAttemptCount()),
                () -> assertEquals(
                        alertId, claim.message().alertId()
                ),
                () -> assertEquals(
                        alert.getEndpoint().getId(),
                        claim.message().endpointId()
                ),
                () -> assertEquals(
                        "Health Check", claim.message().endpointName()
                ),
                () -> assertEquals(
                        "owner@example.com",
                        claim.message().recipientEmail()
                ),
                () -> assertEquals(
                        AlertType.OUTAGE, claim.message().type()
                )
        );

        verify(alertRepository).save(alert);
    }

    @Test
    void sentAlertCannotBeClaimed() {
        alert.setDeliveryStatus(AlertDeliveryStatus.SENT);
        alert.setSentAt(NOW);
        stubAlert();

        assertClaimSkipped();
    }

    @Test
    void threeAttemptsPreventFurtherClaims() {
        alert.setAttemptCount(3);
        alert.setDeliveryStatus(AlertDeliveryStatus.FAILED);
        stubAlert();

        assertClaimSkipped();
    }

    @Test
    void futureRetryIsNotClaimed() {
        alert.setNextAttemptAt(NOW.plusSeconds(1));
        stubAlert();

        assertClaimSkipped();
    }

    @Test
    void activeLeasePreventsAnotherClaim() {
        alert.setDeliveryToken(deliveryToken);
        alert.setDeliveryLeaseUntil(NOW.plusSeconds(1));
        stubAlert();

        assertClaimSkipped();
        assertEquals(deliveryToken, alert.getDeliveryToken());
    }

    @Test
    void expiredLeaseCanBeReclaimedWithNewToken() {
        alert.setAttemptCount(1);
        alert.setDeliveryToken(deliveryToken);
        alert.setDeliveryLeaseUntil(NOW);
        stubAlert();

        ClaimedAlertEmail claim = service.claim(alertId).orElseThrow();

        assertNotEquals(deliveryToken, claim.deliveryToken());
        assertEquals(2, alert.getAttemptCount());
        assertEquals(
                NOW.plusSeconds(120), alert.getDeliveryLeaseUntil()
        );
    }

    @Test
    void missingAlertCannotBeClaimed() {
        when(alertRepository.findByIdForUpdate(alertId))
                .thenReturn(Optional.empty());

        assertClaimSkipped();
    }

    @Test
    void successfulCompletionMarksSentAndClearsLeaseAndError() {
        prepareClaimedAlert();
        alert.setDeliveryStatus(AlertDeliveryStatus.FAILED);
        alert.setLastError("Previous failure");
        stubAlert();

        assertTrue(service.complete(
                alertId, deliveryToken, true, null
        ));

        assertAll(
                () -> assertEquals(
                        AlertDeliveryStatus.SENT,
                        alert.getDeliveryStatus()
                ),
                () -> assertEquals(NOW, alert.getSentAt()),
                () -> assertNull(alert.getLastError()),
                () -> assertNull(alert.getDeliveryToken()),
                () -> assertNull(alert.getDeliveryLeaseUntil()),
                () -> assertEquals(1, alert.getAttemptCount())
        );

        verify(alertRepository).save(alert);
    }

    @Test
    void failedCompletionSchedulesRetryAndClearsLease() {
        prepareClaimedAlert();
        stubAlert();

        assertTrue(service.complete(
                alertId, deliveryToken, false, "SMTP unavailable"
        ));

        assertAll(
                () -> assertEquals(
                        AlertDeliveryStatus.FAILED,
                        alert.getDeliveryStatus()
                ),
                () -> assertEquals(
                        NOW.plusSeconds(60), alert.getNextAttemptAt()
                ),
                () -> assertEquals(
                        "SMTP unavailable", alert.getLastError()
                ),
                () -> assertNull(alert.getSentAt()),
                () -> assertNull(alert.getDeliveryToken()),
                () -> assertNull(alert.getDeliveryLeaseUntil()),
                () -> assertEquals(1, alert.getAttemptCount())
        );

        verify(alertRepository).save(alert);
    }

    @Test
    void staleCompletionTokenDoesNotChangeAlert() {
        prepareClaimedAlert();
        stubAlert();

        assertFalse(service.complete(
                alertId, UUID.randomUUID(), true, null
        ));

        assertEquals(deliveryToken, alert.getDeliveryToken());
        assertEquals(
                AlertDeliveryStatus.PENDING, alert.getDeliveryStatus()
        );

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void missingAlertCompletionReturnsFalse() {
        when(alertRepository.findByIdForUpdate(alertId))
                .thenReturn(Optional.empty());

        assertFalse(service.complete(
                alertId, deliveryToken, true, null
        ));

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void completionCannotBeRepeatedAfterTokenIsConsumed() {
        prepareClaimedAlert();
        stubAlert();

        assertTrue(service.complete(
                alertId, deliveryToken, true, null
        ));
        assertFalse(service.complete(
                alertId, deliveryToken, false, "Late failure"
        ));

        assertEquals(
                AlertDeliveryStatus.SENT, alert.getDeliveryStatus()
        );
        assertNull(alert.getLastError());

        verify(alertRepository, times(1)).save(alert);
    }

    @Test
    void missingIdentifiersAreRejectedBeforeDatabaseAccess() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.claim(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.complete(
                                null, deliveryToken, true, null
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.complete(
                                alertId, null, true, null
                        )
                )
        );

        verifyNoInteractions(alertRepository);
    }

    private void stubAlert() {
        when(alertRepository.findByIdForUpdate(alertId))
                .thenReturn(Optional.of(alert));
    }

    private void prepareClaimedAlert() {
        alert.setAttemptCount(1);
        alert.setDeliveryToken(deliveryToken);
        alert.setDeliveryLeaseUntil(NOW.plusSeconds(120));
    }

    private void assertClaimSkipped() {
        assertTrue(service.claim(alertId).isEmpty());

        verify(alertRepository, never()).save(any(Alert.class));
    }
}