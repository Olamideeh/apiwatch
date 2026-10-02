package com.example.apiwatch.controller;

import com.example.apiwatch.config.SecurityConfig;
import com.example.apiwatch.dto.EndpointResponse;
import com.example.apiwatch.dto.RegisterEndpointRequest;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.exception.GlobalExceptionHandler;
import com.example.apiwatch.exception.InactiveUserException;
import com.example.apiwatch.exception.ResourceNotFoundException;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MonitoredEndpointController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class MonitoredEndpointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EndpointRegistrationService registrationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID ownerId = UUID.randomUUID();

    private static final String BODY = """
            {
              "name": "Health Check",
              "url": "https://example.com/health",
              "expectedStatusCode": 200,
              "intervalSeconds": 60,
              "timeoutMillis": 5000,
              "responseTimeLimitMillis": 1000
            }
            """;

    @Test
    void registrationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(registrationService);
    }

    @Test
    void registrationUsesJwtSubjectAsOwnerAndReturnsCreated()
            throws Exception {
        UUID endpointId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-02T03:00:00Z");

        when(registrationService.register(eq(ownerId), any()))
                .thenReturn(new EndpointResponse(
                        endpointId,
                        "Health Check",
                        "https://example.com/health",
                        200,
                        60,
                        5000,
                        1000,
                        false,
                        EndpointStatus.PENDING,
                        0,
                        now,
                        null,
                        now
                ));

        mockMvc.perform(post("/api/v1/endpoints")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(endpointId.toString()))
                .andExpect(jsonPath("$.currentStatus").value("PENDING"))
                .andExpect(jsonPath("$.paused").value(false));

        verify(registrationService).register(
                ownerId,
                new RegisterEndpointRequest(
                        "Health Check",
                        "https://example.com/health",
                        200,
                        60,
                        5000,
                        1000
                )
        );
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());

        verifyNoInteractions(registrationService);
    }

    @Test
    void intervalBelowMinimumIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace(
                                "\"intervalSeconds\": 60",
                                "\"intervalSeconds\": 59"
                        )))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }

    @Test
    void serviceValidationErrorReturnsBadRequest() throws Exception {
        when(registrationService.register(eq(ownerId), any()))
                .thenThrow(new IllegalArgumentException(
                        "Response time limit cannot exceed timeout"
                ));

        mockMvc.perform(post("/api/v1/endpoints")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace(
                                "\"responseTimeLimitMillis\": 1000",
                                "\"responseTimeLimitMillis\": 6000"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Response time limit cannot exceed timeout"
                ));
    }

    @Test
    void inactiveUserReturnsForbidden() throws Exception {
        when(registrationService.register(eq(ownerId), any()))
                .thenThrow(new InactiveUserException());

        mockMvc.perform(post("/api/v1/endpoints")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingOwnerReturnsNotFound() throws Exception {
        when(registrationService.register(eq(ownerId), any()))
                .thenThrow(new ResourceNotFoundException(
                        "User not found"
                ));

        mockMvc.perform(post("/api/v1/endpoints")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound());
    }
}