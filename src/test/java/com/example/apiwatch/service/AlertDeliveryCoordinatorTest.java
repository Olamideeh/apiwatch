package com.example.apiwatch.service;

import com.example.apiwatch.dto.AlertEmailMessage;
import com.example.apiwatch.dto.ClaimedAlertEmail;
import com.example.apiwatch.enums.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertDeliveryCoordinatorTest {

    @Mock
    private AlertDeliveryStateService stateService;

    @Mock
    private AlertEmailSender emailSender;

    private AlertDeliveryCoordinator coordinator;
    private UUID alertId;
    private UUID token;
    private ClaimedAlertEmail claim;

    @BeforeEach
    void setUp() {
        alertId = UUID.randomUUID();
        token = UUID.randomUUID();

        claim = new ClaimedAlertEmail(
                token,
                new AlertEmailMessage(
                        alertId,
                        UUID.randomUUID(),
                        "Health Check",
                        "owner@example.com",
                        AlertType.OUTAGE,
                        Instant.parse("2026-10-02T04:00:00Z")
                )
        );

        coordinator = new AlertDeliveryCoordinator(
                stateService,
                emailSender
        );
    }

    @Test
    void successfulDeliveryClaimsSendsAndCompletesInOrder() {
        when(stateService.claim(alertId))
                .thenReturn(Optional.of(claim));

        when(stateService.complete(alertId, token, true, null))
                .thenReturn(true);

        assertTrue(coordinator.deliver(alertId));

        InOrder order = inOrder(stateService, emailSender);

        order.verify(stateService).claim(alertId);
        order.verify(emailSender).send(claim.message());
        order.verify(stateService).complete(
                alertId, token, true, null
        );
    }

    @Test
    void unavailableClaimDoesNotSendEmail() {
        when(stateService.claim(alertId))
                .thenReturn(Optional.empty());

        assertFalse(coordinator.deliver(alertId));

        verifyNoInteractions(emailSender);
    }

    @Test
    void smtpFailureRecordsFailedAttemptWithoutImmediateRetry() {
        when(stateService.claim(alertId))
                .thenReturn(Optional.of(claim));

        doThrow(new MailSendException("SMTP unavailable"))
                .when(emailSender)
                .send(claim.message());

        assertFalse(coordinator.deliver(alertId));

        verify(stateService).complete(
                alertId,
                token,
                false,
                "SMTP delivery failed: MailSendException"
        );

        verify(emailSender, times(1)).send(claim.message());
        verify(stateService, never()).complete(
                alertId, token, true, null
        );
    }

    @Test
    void databaseFailureAfterSendingIsNotMarkedAsSmtpFailure() {
        when(stateService.claim(alertId))
                .thenReturn(Optional.of(claim));

        when(stateService.complete(alertId, token, true, null))
                .thenThrow(new IllegalStateException(
                        "Database unavailable"
                ));

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.deliver(alertId)
        );

        verify(emailSender, times(1)).send(claim.message());
        verifyNoMoreInteractions(emailSender);

        verify(stateService).claim(alertId);
        verify(stateService).complete(alertId, token, true, null);
        verifyNoMoreInteractions(stateService);
    }
}