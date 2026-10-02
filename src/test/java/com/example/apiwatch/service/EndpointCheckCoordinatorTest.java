package com.example.apiwatch.service;

import com.example.apiwatch.dto.*;
import com.example.apiwatch.enums.EndpointStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointCheckCoordinatorTest {

    private static final Instant NOW =
            Instant.parse("2026-10-02T03:00:00Z");

    @Mock
    private CheckClaimService claimService;

    @Mock
    private HttpProbeService probeService;

    @Mock
    private CheckRecordingService recordingService;

    private EndpointCheckCoordinator coordinator;
    private UUID endpointId;
    private UUID checkToken;
    private ClaimedEndpointCheck claim;
    private HttpProbeResult probe;

    @BeforeEach
    void setUp() {
        endpointId = UUID.randomUUID();
        checkToken = UUID.randomUUID();

        claim = new ClaimedEndpointCheck(
                endpointId,
                checkToken,
                "https://example.com/health",
                5000
        );

        probe = new HttpProbeResult(200, 100, null);

        coordinator = new EndpointCheckCoordinator(
                claimService,
                probeService,
                recordingService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void claimsThenProbesThenRecordsUsingClaimToken() {
        CheckResultResponse response = new CheckResultResponse(
                UUID.randomUUID(),
                endpointId,
                EndpointStatus.ONLINE,
                200,
                100,
                null,
                NOW
        );

        stubClaimAndProbe();

        when(recordingService.record(
                endpointId, checkToken, probe, NOW
        )).thenReturn(Optional.of(response));

        assertEquals(
                Optional.of(response),
                coordinator.check(endpointId)
        );

        InOrder order = inOrder(
                claimService,
                probeService,
                recordingService
        );

        order.verify(claimService).claim(endpointId);
        order.verify(probeService).check(claim.url(), 5000);
        order.verify(recordingService).record(
                endpointId, checkToken, probe, NOW
        );
    }

    @Test
    void unavailableClaimDoesNotProbeOrRecord() {
        when(claimService.claim(endpointId))
                .thenReturn(Optional.empty());

        assertTrue(coordinator.check(endpointId).isEmpty());

        verifyNoInteractions(probeService, recordingService);
    }

    @Test
    void discardedResultReturnsEmpty() {
        stubClaimAndProbe();

        when(recordingService.record(
                endpointId, checkToken, probe, NOW
        )).thenReturn(Optional.empty());

        assertTrue(coordinator.check(endpointId).isEmpty());
    }

    @Test
    void recordingFailureIsPropagatedWithoutRepeatingProbe() {
        stubClaimAndProbe();

        when(recordingService.record(
                endpointId, checkToken, probe, NOW
        )).thenThrow(new IllegalStateException("Database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.check(endpointId)
        );

        verify(probeService, times(1)).check(claim.url(), 5000);
        verify(recordingService, times(1)).record(
                endpointId, checkToken, probe, NOW
        );
    }

    private void stubClaimAndProbe() {
        when(claimService.claim(endpointId))
                .thenReturn(Optional.of(claim));

        when(probeService.check(claim.url(), 5000))
                .thenReturn(probe);
    }
}