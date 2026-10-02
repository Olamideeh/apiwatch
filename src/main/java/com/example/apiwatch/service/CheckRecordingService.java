package com.example.apiwatch.service;

import com.example.apiwatch.dto.CheckResultResponse;
import com.example.apiwatch.dto.HttpProbeResult;
import com.example.apiwatch.dto.MonitoringTransition;
import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.entity.CheckResult;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.enums.AlertDeliveryStatus;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.exception.ResourceNotFoundException;
import com.example.apiwatch.repository.AlertRepository;
import com.example.apiwatch.repository.CheckResultRepository;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckRecordingService {

    private final MonitoredEndpointRepository endpointRepository;
    private final CheckResultRepository checkResultRepository;
    private final AlertRepository alertRepository;
    private final EndpointStatusClassifier classifier;
    private final MonitoringTransitionService transitionService;

    @Transactional
    public Optional<CheckResultResponse> record(
            UUID endpointId,
            UUID checkToken,
            HttpProbeResult probe,
            Instant checkedAt
    ) {
        validateArguments(endpointId, checkToken, probe, checkedAt);

        MonitoredEndpoint endpoint = endpointRepository
                .findByIdForUpdate(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Endpoint not found"
                ));

        if (!checkToken.equals(endpoint.getCheckToken())) {
            return Optional.empty();
        }

        if (endpoint.isPaused() || !endpoint.getOwner().isActive()) {
            return Optional.empty();
        }

        EndpointStatus status = classifier.classify(
                endpoint.getExpectedStatusCode(),
                probe.actualStatusCode(),
                probe.responseTimeMillis(),
                endpoint.getResponseTimeLimitMillis()
        );

        MonitoringTransition transition = transitionService.transition(
                endpoint.getConsecutiveFailures(),
                endpoint.isOutageOpen(),
                status
        );

        CheckResult result = CheckResult.builder()
                .endpoint(endpoint)
                .status(status)
                .actualStatusCode(probe.actualStatusCode())
                .responseTimeMillis(probe.responseTimeMillis())
                .errorMessage(limitError(probe.errorMessage()))
                .checkedAt(checkedAt)
                .build();

        CheckResult saved = checkResultRepository.save(result);

        endpoint.setCurrentStatus(status);
        endpoint.setConsecutiveFailures(
                transition.consecutiveFailures()
        );
        endpoint.setOutageOpen(transition.outageOpen());
        endpoint.setLastCheckedAt(checkedAt);
        endpoint.setNextCheckAt(
                checkedAt.plusSeconds(endpoint.getIntervalSeconds())
        );

        endpoint.setCheckToken(null);
        endpoint.setCheckLeaseUntil(null);

        endpointRepository.save(endpoint);

        transition.alertType().ifPresent(type -> {
            Alert alert = Alert.builder()
                    .endpoint(endpoint)
                    .type(type)
                    .recipientEmail(endpoint.getOwner().getEmail())
                    .deliveryStatus(AlertDeliveryStatus.PENDING)
                    .attemptCount(0)
                    .build();

            alertRepository.save(alert);
        });

        return Optional.of(new CheckResultResponse(
                saved.getId(),
                endpoint.getId(),
                saved.getStatus(),
                saved.getActualStatusCode(),
                saved.getResponseTimeMillis(),
                saved.getErrorMessage(),
                saved.getCheckedAt()
        ));
    }

    private void validateArguments(
            UUID endpointId,
            UUID checkToken,
            HttpProbeResult probe,
            Instant checkedAt
    ) {
        if (endpointId == null) {
            throw new IllegalArgumentException(
                    "Endpoint ID is required"
            );
        }

        if (checkToken == null) {
            throw new IllegalArgumentException(
                    "Check token is required"
            );
        }

        if (probe == null) {
            throw new IllegalArgumentException(
                    "Probe result is required"
            );
        }

        if (checkedAt == null) {
            throw new IllegalArgumentException(
                    "Check timestamp is required"
            );
        }

        if (probe.responseTimeMillis() < 0) {
            throw new IllegalArgumentException(
                    "Response time cannot be negative"
            );
        }

        Integer statusCode = probe.actualStatusCode();

        if (statusCode != null && (statusCode < 100 || statusCode > 599)) {
            throw new IllegalArgumentException(
                    "HTTP status code must be between 100 and 599"
            );
        }
    }

    private String limitError(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= 2000) {
            return errorMessage;
        }

        return errorMessage.substring(0, 2000);
    }
}