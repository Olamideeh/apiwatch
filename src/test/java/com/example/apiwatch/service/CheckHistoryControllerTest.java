package com.example.apiwatch.controller;

import com.example.apiwatch.config.SecurityConfig;
import com.example.apiwatch.dto.CheckResultResponse;
import com.example.apiwatch.dto.PageResponse;
import com.example.apiwatch.dto.UptimeResponse;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.exception.GlobalExceptionHandler;
import com.example.apiwatch.exception.ResourceNotFoundException;
import com.example.apiwatch.service.CheckHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CheckHistoryController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class CheckHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckHistoryService historyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID endpointId = UUID.randomUUID();

    private final Instant from =
            Instant.parse("2026-10-01T00:00:00Z");

    private final Instant to =
            Instant.parse("2026-10-02T00:00:00Z");

    @Test
    void historyRequiresAuthentication() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/checks", endpointId
                ))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(historyService);
    }

    @Test
    void uptimeRequiresAuthentication() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/uptime", endpointId
                )
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(historyService);
    }

    @Test
    void historyUsesJwtOwnerAndDefaultPagination() throws Exception {
        UUID checkId = UUID.randomUUID();

        when(historyService.getHistory(ownerId, endpointId, 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(new CheckResultResponse(
                                checkId,
                                endpointId,
                                EndpointStatus.ONLINE,
                                200,
                                100,
                                null,
                                from
                        )),
                        0, 20, 1, 1, true
                ));

        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/checks", endpointId
                )
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id")
                        .value(checkId.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(historyService).getHistory(
                ownerId, endpointId, 0, 20
        );
    }

    @Test
    void historyForwardsExplicitPagination() throws Exception {
        when(historyService.getHistory(ownerId, endpointId, 2, 10))
                .thenReturn(new PageResponse<>(
                        List.of(), 2, 10, 0, 0, true
                ));

        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/checks", endpointId
                )
                        .param("page", "2")
                        .param("size", "10")
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isOk());

        verify(historyService).getHistory(
                ownerId, endpointId, 2, 10
        );
    }

    @Test
    void unavailableHistoryReturnsNotFound() throws Exception {
        when(historyService.getHistory(ownerId, endpointId, 0, 20))
                .thenThrow(new ResourceNotFoundException(
                        "Endpoint not found"
                ));

        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/checks", endpointId
                )
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isNotFound());
    }

    @Test
    void uptimeParsesTimestampsAndUsesJwtOwner() throws Exception {
        when(historyService.getUptime(
                ownerId, endpointId, from, to
        )).thenReturn(new UptimeResponse(
                endpointId,
                from,
                to,
                3,
                2,
                1,
                new BigDecimal("66.67")
        ));

        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/uptime", endpointId
                )
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChecks").value(3))
                .andExpect(jsonPath("$.availableChecks").value(2))
                .andExpect(jsonPath("$.offlineChecks").value(1))
                .andExpect(jsonPath("$.uptimePercentage").value(66.67));

        verify(historyService).getUptime(
                ownerId, endpointId, from, to
        );
    }

    @Test
    void missingTimeParameterIsRejected() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/uptime", endpointId
                )
                        .param("from", from.toString())
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(historyService);
    }

    @Test
    void malformedTimestampIsRejected() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/uptime", endpointId
                )
                        .param("from", "not-a-timestamp")
                        .param("to", to.toString())
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(historyService);
    }

    @Test
    void invalidRangeReturnsBadRequest() throws Exception {
        when(historyService.getUptime(
                ownerId, endpointId, to, from
        )).thenThrow(new IllegalArgumentException(
                "From timestamp must be before to timestamp"
        ));

        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/uptime", endpointId
                )
                        .param("from", to.toString())
                        .param("to", from.toString())
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unavailableUptimeReturnsNotFound() throws Exception {
        when(historyService.getUptime(
                ownerId, endpointId, from, to
        )).thenThrow(new ResourceNotFoundException(
                "Endpoint not found"
        ));

        mockMvc.perform(get(
                        "/api/v1/endpoints/{id}/uptime", endpointId
                )
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(jwt().jwt(token ->
                                token.subject(ownerId.toString())
                        )))
                .andExpect(status().isNotFound());
    }
}