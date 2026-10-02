package com.example.apiwatch.service;

import com.example.apiwatch.dto.EndpointResponse;
import com.example.apiwatch.dto.RegisterEndpointRequest;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.exception.InactiveUserException;
import com.example.apiwatch.exception.ResourceNotFoundException;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
import com.example.apiwatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EndpointRegistrationService {

    private final UserRepository userRepository;
    private final MonitoredEndpointRepository endpointRepository;
    private final EndpointUrlValidator urlValidator;

    @Transactional
    public EndpointResponse register(
            UUID ownerId,
            RegisterEndpointRequest request
    ) {
        validateRequest(ownerId, request);

        URI uri = urlValidator.validate(request.url());

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"
                ));

        if (!owner.isActive()) {
            throw new InactiveUserException();
        }

        MonitoredEndpoint endpoint = MonitoredEndpoint.builder()
                .owner(owner)
                .name(request.name().trim())
                .url(uri.toString())
                .expectedStatusCode(request.expectedStatusCode())
                .intervalSeconds(request.intervalSeconds())
                .timeoutMillis(request.timeoutMillis())
                .responseTimeLimitMillis(
                        request.responseTimeLimitMillis()
                )
                .paused(false)
                .currentStatus(EndpointStatus.PENDING)
                .consecutiveFailures(0)
                .outageOpen(false)
                .nextCheckAt(Instant.now())
                .build();

        MonitoredEndpoint saved = endpointRepository.save(endpoint);

        return new EndpointResponse(
                saved.getId(),
                saved.getName(),
                saved.getUrl(),
                saved.getExpectedStatusCode(),
                saved.getIntervalSeconds(),
                saved.getTimeoutMillis(),
                saved.getResponseTimeLimitMillis(),
                saved.isPaused(),
                saved.getCurrentStatus(),
                saved.getConsecutiveFailures(),
                saved.getNextCheckAt(),
                saved.getLastCheckedAt(),
                saved.getCreatedAt()
        );
    }

    private void validateRequest(
            UUID ownerId,
            RegisterEndpointRequest request
    ) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID is required");
        }

        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException(
                    "Endpoint name is required"
            );
        }

        if (request.name().length() > 150) {
            throw new IllegalArgumentException(
                    "Endpoint name cannot exceed 150 characters"
            );
        }

        requireRange(
                request.expectedStatusCode(),
                100,
                599,
                "Expected status code"
        );

        requireRange(
                request.intervalSeconds(),
                60,
                86400,
                "Check interval"
        );

        requireRange(
                request.timeoutMillis(),
                100,
                30000,
                "Timeout"
        );

        requireRange(
                request.responseTimeLimitMillis(),
                1,
                30000,
                "Response time limit"
        );

        if (request.responseTimeLimitMillis()
                > request.timeoutMillis()) {
            throw new IllegalArgumentException(
                    "Response time limit cannot exceed timeout"
            );
        }
    }

    private void requireRange(
            Integer value,
            int minimum,
            int maximum,
            String field
    ) {
        if (value == null || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " must be between "
                            + minimum + " and " + maximum
            );
        }
    }
}