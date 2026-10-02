package com.example.apiwatch.service;

import com.example.apiwatch.dto.ClaimedAlertEmail;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertDeliveryCoordinator {

    private final AlertDeliveryStateService stateService;
    private final AlertEmailSender emailSender;

    public boolean deliver(UUID alertId) {
        Optional<ClaimedAlertEmail> claimed =
                stateService.claim(alertId);

        if (claimed.isEmpty()) {
            return false;
        }

        ClaimedAlertEmail claim = claimed.get();

        try {
            emailSender.send(claim.message());
        } catch (MailException exception) {
            stateService.complete(
                    alertId,
                    claim.deliveryToken(),
                    false,
                    "SMTP delivery failed: "
                            + exception.getClass().getSimpleName()
            );

            return false;
        }

        return stateService.complete(
                alertId,
                claim.deliveryToken(),
                true,
                null
        );
    }
}