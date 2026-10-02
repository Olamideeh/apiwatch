package com.example.apiwatch.controller;

import com.example.apiwatch.config.SecurityConfig;
import com.example.apiwatch.dto.EndpointResponse;
import com.example.apiwatch.dto.PageResponse;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.exception.GlobalExceptionHandler;
import com.example.apiwatch.exception.ResourceNotFoundException;
import com.example.apiwatch.service.EndpointManagementService;
import com.example.apiwatch.service.EndpointRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MonitoredEndpointController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class EndpointManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EndpointRegistrationService registrationService;

    @MockitoBean
    private EndpointManagementService managementService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID endpointId = UUID.randomUUID();

    @Test
    void retrievalRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/{id}", endpointId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(managementService);
    }

    @Test
    void listingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(managementService);
    }

    @Test
    void monitoringUpdateRequiresAuthentication() throws Exception {
        mockMvc.perform(patch(
                        "/api/v1/endpoints/{id}/monitoring", endpointId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paused\":true}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(managementService);
    }

    @Test
    void retrievesEndpointUsingJwtOwner() throws Exception {
        when(managementService.getEndpoint(ownerId, endpointId))
                .thenReturn(response(false));

        mockMvc.perform(get("/api/v1/endpoints/{id}", endpointId)
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(endpointId.toString()));

        verify(managementService).getEndpoint(ownerId, endpointId);
    }

    @Test
    void unavailableEndpointReturnsNotFound() throws Exception {
        when(managementService.getEndpoint(ownerId, endpointId))
                .thenThrow(new ResourceNotFoundException(
                        "Endpoint not found"
                ));

        mockMvc.perform(get("/api/v1/endpoints/{id}", endpointId)
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingUsesDefaultPagination() throws Exception {
        when(managementService.listEndpoints(ownerId, 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(response(false)),
                        0, 20, 1, 1, true
                ));

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id")
                        .value(endpointId.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(managementService).listEndpoints(ownerId, 0, 20);
    }

    @Test
    void listingForwardsExplicitPagination() throws Exception {
        when(managementService.listEndpoints(ownerId, 2, 10))
                .thenReturn(new PageResponse<>(
                        List.of(), 2, 10, 0, 0, true
                ));

        mockMvc.perform(get("/api/v1/endpoints")
                        .param("page", "2")
                        .param("size", "10")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isOk());

        verify(managementService).listEndpoints(ownerId, 2, 10);
    }

    @Test
    void invalidPaginationReturnsBadRequest() throws Exception {
        when(managementService.listEndpoints(ownerId, -1, 20))
                .thenThrow(new IllegalArgumentException(
                        "Page number cannot be negative"
                ));

        mockMvc.perform(get("/api/v1/endpoints")
                        .param("page", "-1")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pausesEndpointUsingJwtOwner() throws Exception {
        when(managementService.updateMonitoringState(
                ownerId, endpointId, true
        )).thenReturn(response(true));

        mockMvc.perform(patch(
                        "/api/v1/endpoints/{id}/monitoring", endpointId
                )
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paused\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true));

        verify(managementService).updateMonitoringState(
                ownerId, endpointId, true
        );
    }

    @Test
    void resumesEndpointUsingJwtOwner() throws Exception {
        when(managementService.updateMonitoringState(
                ownerId, endpointId, false
        )).thenReturn(response(false));

        mockMvc.perform(patch(
                        "/api/v1/endpoints/{id}/monitoring", endpointId
                )
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paused\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(false));

        verify(managementService).updateMonitoringState(
                ownerId, endpointId, false
        );
    }

    @Test
    void missingPausedStateIsRejected() throws Exception {
        mockMvc.perform(patch(
                        "/api/v1/endpoints/{id}/monitoring", endpointId
                )
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(managementService);
    }

    @Test
    void malformedEndpointIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/not-a-uuid")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(managementService);
    }

    private EndpointResponse response(boolean paused) {
        Instant now = Instant.parse("2026-10-02T03:00:00Z");

        return new EndpointResponse(
                endpointId,
                "Health Check",
                "https://example.com/health",
                200,
                60,
                5000,
                1000,
                paused,
                EndpointStatus.PENDING,
                0,
                now,
                null,
                now
        );
    }
}