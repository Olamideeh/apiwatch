package com.example.apiwatch.controller;

import com.example.apiwatch.dto.EndpointResponse;
import com.example.apiwatch.dto.PageResponse;
import com.example.apiwatch.dto.RegisterEndpointRequest;
import com.example.apiwatch.dto.UpdateMonitoringStateRequest;
import com.example.apiwatch.service.EndpointManagementService;
import com.example.apiwatch.service.EndpointRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endpoints")
@RequiredArgsConstructor
public class MonitoredEndpointController {

    private final EndpointRegistrationService registrationService;
    private final EndpointManagementService managementService;

    @PostMapping
    public ResponseEntity<EndpointResponse> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterEndpointRequest request
    ) {
        UUID ownerId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(ownerId, request));
    }

    @GetMapping("/{endpointId}")
    public ResponseEntity<EndpointResponse> getEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID endpointId
    ) {
        UUID ownerId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                managementService.getEndpoint(ownerId, endpointId)
        );
    }

    @GetMapping
    public ResponseEntity<PageResponse<EndpointResponse>> listEndpoints(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID ownerId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                managementService.listEndpoints(ownerId, page, size)
        );
    }

    @PatchMapping("/{endpointId}/monitoring")
    public ResponseEntity<EndpointResponse> updateMonitoringState(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID endpointId,
            @Valid @RequestBody UpdateMonitoringStateRequest request
    ) {
        UUID ownerId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                managementService.updateMonitoringState(
                        ownerId,
                        endpointId,
                        request.paused()
                )
        );
    }
}