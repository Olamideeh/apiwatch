package com.example.apiwatch.service;

import com.example.apiwatch.dto.ClaimedEndpointCheck;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckClaimService {

    private final MonitoredEndpointRepository endpointRepository;
    private final Clock clock;

    @Transactional
    public Optional<ClaimedEndpointCheck> claim(UUID endpointId) {
        if (endpointId == null) {
            throw new IllegalArgumentException(
                    "Endpoint ID is required"
            );
        }

        Optional<MonitoredEndpoint> found =
                endpointRepository.findByIdForUpdate(endpointId);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        MonitoredEndpoint endpoint = found.get();
        Instant now = clock.instant();

        if (endpoint.isPaused()
                || !endpoint.getOwner().isActive()
                || endpoint.getNextCheckAt().isAfter(now)) {
            return Optional.empty();
        }

        Instant leaseUntil = endpoint.getCheckLeaseUntil();

        if (leaseUntil != null && leaseUntil.isAfter(now)) {
            return Optional.empty();
        }

        UUID token = UUID.randomUUID();

        endpoint.setCheckToken(token);
        endpoint.setCheckLeaseUntil(now.plusSeconds(120));

        endpointRepository.save(endpoint);

        return Optional.of(new ClaimedEndpointCheck(
                endpoint.getId(),
                token,
                endpoint.getUrl(),
                endpoint.getTimeoutMillis()
        ));
    }
}