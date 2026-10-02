package com.example.apiwatch.controller;

import com.example.apiwatch.dto.EndpointResponse;
import com.example.apiwatch.dto.RegisterEndpointRequest;
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

    @PostMapping
    public ResponseEntity<EndpointResponse> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterEndpointRequest request
    ) {
        UUID ownerId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(ownerId, request));
    }
}