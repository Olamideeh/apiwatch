package com.example.apiwatch.service;

import com.example.apiwatch.dto.AlertEmailMessage;
import com.example.apiwatch.enums.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    private AlertEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new AlertEmailSender(mailSender);

        ReflectionTestUtils.setField(
                sender,
                "fromAddress",
                "no-reply@apiwatch.test"
        );
    }

    @Test
    void sendsOutageEmailToRecordedRecipient() {
        AlertEmailMessage alert = message(AlertType.OUTAGE);

        sender.send(alert);

        SimpleMailMessage email = capturedEmail();

        assertAll(
                () -> assertEquals(
                        "no-reply@apiwatch.test", email.getFrom()
                ),
                () -> assertArrayEquals(
                        new String[]{"owner@example.com"},
                        email.getTo()
                ),
                () -> assertEquals(
                        "APIWatch outage notification",
                        email.getSubject()
                ),
                () -> assertNotNull(email.getText()),
                () -> assertTrue(
                        email.getText().contains("Health Check")
                ),
                () -> assertTrue(
                        email.getText().contains(
                                alert.endpointId().toString()
                        )
                ),
                () -> assertTrue(
                        email.getText().contains(
                                alert.alertId().toString()
                        )
                )
        );
    }

    @Test
    void sendsRecoveryEmailWithRecoverySubject() {
        sender.send(message(AlertType.RECOVERY));

        SimpleMailMessage email = capturedEmail();

        assertEquals(
                "APIWatch recovery notification",
                email.getSubject()
        );
        assertNotNull(email.getText());
        assertTrue(email.getText().contains("ONLINE"));
    }

    @Test
    void mailFailureIsPropagatedWithoutImmediateRetry() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThrows(
                MailSendException.class,
                () -> sender.send(message(AlertType.OUTAGE))
        );

        verify(mailSender, times(1))
                .send(any(SimpleMailMessage.class));
    }

    private AlertEmailMessage message(AlertType type) {
        return new AlertEmailMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Health Check",
                "owner@example.com",
                type,
                Instant.parse("2026-10-02T04:00:00Z")
        );
    }

    private SimpleMailMessage capturedEmail() {
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        return captor.getValue();
    }
}