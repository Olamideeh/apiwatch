package com.example.apiwatch.service;

import com.example.apiwatch.dto.ClaimedEndpointCheck;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
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
class CheckClaimServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-10-02T03:00:00Z");

    @Mock
    private MonitoredEndpointRepository endpointRepository;

    private CheckClaimService service;
    private MonitoredEndpoint endpoint;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        endpointId = UUID.randomUUID();

        endpoint = MonitoredEndpoint.builder()
                .id(endpointId)
                .owner(User.builder().active(true).build())
                .url("https://example.com/health")
                .nextCheckAt(NOW)
                .build();

        service = new CheckClaimService(
                endpointRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void claimsDueEndpointAndReturnsRequestSnapshot() {
        stubEndpoint();

        ClaimedEndpointCheck claim =
                service.claim(endpointId).orElseThrow();

        assertAll(
                () -> assertEquals(endpointId, claim.endpointId()),
                () -> assertNotNull(claim.checkToken()),
                () -> assertEquals(
                        claim.checkToken(), endpoint.getCheckToken()
                ),
                () -> assertEquals(
                        NOW.plusSeconds(120),
                        endpoint.getCheckLeaseUntil()
                ),
                () -> assertEquals(
                        "https://example.com/health", claim.url()
                ),
                () -> assertEquals(5000, claim.timeoutMillis())
        );

        verify(endpointRepository).findByIdForUpdate(endpointId);
        verify(endpointRepository).save(endpoint);
    }

    @Test
    void futureEndpointIsNotClaimed() {
        endpoint.setNextCheckAt(NOW.plusSeconds(1));
        stubEndpoint();

        assertSkipped();
    }

    @Test
    void pausedEndpointIsNotClaimed() {
        endpoint.setPaused(true);
        stubEndpoint();

        assertSkipped();
    }

    @Test
    void inactiveOwnerEndpointIsNotClaimed() {
        endpoint.getOwner().setActive(false);
        stubEndpoint();

        assertSkipped();
    }

    @Test
    void activeLeasePreventsAnotherClaim() {
        UUID existingToken = UUID.randomUUID();
        endpoint.setCheckToken(existingToken);
        endpoint.setCheckLeaseUntil(NOW.plusSeconds(1));
        stubEndpoint();

        assertSkipped();
        assertEquals(existingToken, endpoint.getCheckToken());
    }

    @Test
    void expiredLeaseCanBeReplaced() {
        UUID oldToken = UUID.randomUUID();
        endpoint.setCheckToken(oldToken);
        endpoint.setCheckLeaseUntil(NOW.minusSeconds(1));
        stubEndpoint();

        ClaimedEndpointCheck claim =
                service.claim(endpointId).orElseThrow();

        assertNotEquals(oldToken, claim.checkToken());
        assertEquals(
                NOW.plusSeconds(120), endpoint.getCheckLeaseUntil()
        );

        verify(endpointRepository).save(endpoint);
    }

    @Test
    void leaseExactlyAtExpiryCanBeReplaced() {
        UUID oldToken = UUID.randomUUID();
        endpoint.setCheckToken(oldToken);
        endpoint.setCheckLeaseUntil(NOW);
        stubEndpoint();

        ClaimedEndpointCheck claim =
                service.claim(endpointId).orElseThrow();

        assertNotEquals(oldToken, claim.checkToken());
    }

    @Test
    void missingEndpointIsSkipped() {
        when(endpointRepository.findByIdForUpdate(endpointId))
                .thenReturn(Optional.empty());

        assertSkipped();
    }

    @Test
    void missingEndpointIdIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.claim(null)
        );

        verifyNoInteractions(endpointRepository);
    }

    private void stubEndpoint() {
        when(endpointRepository.findByIdForUpdate(endpointId))
                .thenReturn(Optional.of(endpoint));
    }

    private void assertSkipped() {
        assertTrue(service.claim(endpointId).isEmpty());

        verify(endpointRepository, never())
                .save(any(MonitoredEndpoint.class));
    }
}