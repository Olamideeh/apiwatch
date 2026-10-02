package com.example.apiwatch.service;

import com.example.apiwatch.dto.AlertEmailMessage;
import com.example.apiwatch.enums.AlertType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlertEmailSender {

    private final JavaMailSender mailSender;

    @Value("${apiwatch.alerts.from}")
    private String fromAddress;

    public void send(AlertEmailMessage alert) {
        SimpleMailMessage email = new SimpleMailMessage();

        email.setFrom(fromAddress);
        email.setTo(alert.recipientEmail());

        if (alert.type() == AlertType.OUTAGE) {
            email.setSubject("APIWatch outage notification");
            email.setText("""
                    APIWatch detected an outage after repeated OFFLINE checks.

                    Endpoint: %s
                    Endpoint ID: %s
                    Alert ID: %s
                    Detected at: %s

                    Sign in to APIWatch to inspect the check history.
                    """.formatted(
                    alert.endpointName(),
                    alert.endpointId(),
                    alert.alertId(),
                    alert.createdAt()
            ));
        } else {
            email.setSubject("APIWatch recovery notification");
            email.setText("""
                    The endpoint returned to ONLINE after an outage.

                    Endpoint: %s
                    Endpoint ID: %s
                    Alert ID: %s
                    Detected at: %s

                    Sign in to APIWatch to inspect the check history.
                    """.formatted(
                    alert.endpointName(),
                    alert.endpointId(),
                    alert.alertId(),
                    alert.createdAt()
            ));
        }

        mailSender.send(email);
    }
}