package com.example.apiwatch.service;

import com.example.apiwatch.dto.EndpointResponse;
import com.example.apiwatch.dto.PageResponse;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.exception.InactiveUserException;
import com.example.apiwatch.exception.ResourceNotFoundException;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
import com.example.apiwatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EndpointManagementService {

    private final UserRepository userRepository;
    private final MonitoredEndpointRepository endpointRepository;

    @Transactional(readOnly = true)
    public EndpointResponse getEndpoint(
            UUID ownerId,
            UUID endpointId
    ) {
        requireActiveOwner(ownerId);
        return toResponse(findOwnedEndpoint(ownerId, endpointId));
    }

    @Transactional(readOnly = true)
    public PageResponse<EndpointResponse> listEndpoints(
            UUID ownerId,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }

        requireActiveOwner(ownerId);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<MonitoredEndpoint> result =
                endpointRepository.findByOwner_Id(ownerId, pageable);

        return new PageResponse<>(
                result.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()
        );
    }

    @Transactional
    public EndpointResponse updateMonitoringState(
            UUID ownerId,
            UUID endpointId,
            boolean paused
    ) {
        requireActiveOwner(ownerId);

        MonitoredEndpoint endpoint =
                findOwnedEndpoint(ownerId, endpointId);

        if (endpoint.isPaused() == paused) {
            return toResponse(endpoint);
        }

        endpoint.setPaused(paused);

        if (!paused) {
            endpoint.setNextCheckAt(Instant.now());
        }

        return toResponse(endpointRepository.save(endpoint));
    }

    private void requireActiveOwner(UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException(
                    "Owner ID is required"
            );
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"
                ));

        if (!owner.isActive()) {
            throw new InactiveUserException();
        }
    }

    private MonitoredEndpoint findOwnedEndpoint(
            UUID ownerId,
            UUID endpointId
    ) {
        if (endpointId == null) {
            throw new IllegalArgumentException(
                    "Endpoint ID is required"
            );
        }

        return endpointRepository
                .findByIdAndOwner_Id(endpointId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Endpoint not found"
                ));
    }

    private EndpointResponse toResponse(MonitoredEndpoint endpoint) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getUrl(),
                endpoint.getExpectedStatusCode(),
                endpoint.getIntervalSeconds(),
                endpoint.getTimeoutMillis(),
                endpoint.getResponseTimeLimitMillis(),
                endpoint.isPaused(),
                endpoint.getCurrentStatus(),
                endpoint.getConsecutiveFailures(),
                endpoint.getNextCheckAt(),
                endpoint.getLastCheckedAt(),
                endpoint.getCreatedAt()
        );
    }
}