package com.example.apiwatch.controller;

import com.example.apiwatch.dto.CheckResultResponse;
import com.example.apiwatch.dto.PageResponse;
import com.example.apiwatch.dto.UptimeResponse;
import com.example.apiwatch.service.CheckHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endpoints")
@RequiredArgsConstructor
public class CheckHistoryController {

    private final CheckHistoryService historyService;

    @GetMapping("/{endpointId}/checks")
    public ResponseEntity<PageResponse<CheckResultResponse>> getHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID endpointId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID ownerId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                historyService.getHistory(
                        ownerId,
                        endpointId,
                        page,
                        size
                )
        );
    }

    @GetMapping("/{endpointId}/uptime")
    public ResponseEntity<UptimeResponse> getUptime(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID endpointId,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        UUID ownerId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                historyService.getUptime(
                        ownerId,
                        endpointId,
                        from,
                        to
                )
        );
    }
}