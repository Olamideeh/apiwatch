package com.example.apiwatch.service;

import com.example.apiwatch.dto.CheckResultResponse;
import com.example.apiwatch.dto.ClaimedEndpointCheck;
import com.example.apiwatch.dto.HttpProbeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EndpointCheckCoordinator {

    private final CheckClaimService claimService;
    private final HttpProbeService probeService;
    private final CheckRecordingService recordingService;
    private final Clock clock;

    public Optional<CheckResultResponse> check(UUID endpointId) {
        Optional<ClaimedEndpointCheck> claimed =
                claimService.claim(endpointId);

        if (claimed.isEmpty()) {
            return Optional.empty();
        }

        ClaimedEndpointCheck claim = claimed.get();

        HttpProbeResult probe = probeService.check(
                claim.url(),
                claim.timeoutMillis()
        );

        return recordingService.record(
                claim.endpointId(),
                claim.checkToken(),
                probe,
                clock.instant()
        );
    }
}