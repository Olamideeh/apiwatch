package com.example.apiwatch.service;

import com.example.apiwatch.dto.EndpointResponse;
import com.example.apiwatch.dto.RegisterEndpointRequest;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.exception.InactiveUserException;
import com.example.apiwatch.exception.ResourceNotFoundException;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
import com.example.apiwatch.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MonitoredEndpointRepository endpointRepository;

    private EndpointRegistrationService service;
    private User owner;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();

        owner = User.builder()
                .id(ownerId)
                .email("user@example.com")
                .active(true)
                .build();

        service = new EndpointRegistrationService(
                userRepository,
                endpointRepository,
                new EndpointUrlValidator()
        );
    }

    @Test
    void registersPendingEndpointOwnedByAuthenticatedUser() {
        UUID endpointId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-02T03:00:00Z");

        stubOwner();

        when(endpointRepository.save(any(MonitoredEndpoint.class)))
                .thenAnswer(invocation -> {
                    MonitoredEndpoint endpoint = invocation.getArgument(0);
                    endpoint.setId(endpointId);
                    endpoint.setCreatedAt(createdAt);
                    return endpoint;
                });

        EndpointResponse response = service.register(
                ownerId,
                validRequest()
        );

        ArgumentCaptor<MonitoredEndpoint> captor =
                ArgumentCaptor.forClass(MonitoredEndpoint.class);

        verify(endpointRepository).save(captor.capture());
        MonitoredEndpoint saved = captor.getValue();

        assertAll(
                () -> assertSame(owner, saved.getOwner()),
                () -> assertEquals(endpointId, response.id()),
                () -> assertEquals("Health Check", response.name()),
                () -> assertEquals(
                        "https://example.com/health",
                        response.url()
                ),
                () -> assertEquals(200, response.expectedStatusCode()),
                () -> assertEquals(60, response.intervalSeconds()),
                () -> assertEquals(5000, response.timeoutMillis()),
                () -> assertEquals(
                        1000,
                        response.responseTimeLimitMillis()
                ),
                () -> assertEquals(
                        EndpointStatus.PENDING,
                        response.currentStatus()
                ),
                () -> assertFalse(response.paused()),
                () -> assertEquals(0, response.consecutiveFailures()),
                () -> assertFalse(saved.isOutageOpen()),
                () -> assertNull(response.lastCheckedAt()),
                () -> assertNotNull(response.nextCheckAt()),
                () -> assertEquals(createdAt, response.createdAt())
        );
    }

    @Test
    void trimsNameAndUrl() {
        stubOwner();
        stubSave();

        EndpointResponse response = service.register(
                ownerId,
                new RegisterEndpointRequest(
                        "  Health Check  ",
                        "  https://example.com/health  ",
                        200, 60, 5000, 1000
                )
        );

        assertEquals("Health Check", response.name());
        assertEquals("https://example.com/health", response.url());
    }

    @Test
    void unknownOwnerIsRejected() {
        when(userRepository.findById(ownerId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.register(ownerId, validRequest())
        );

        verifyNoInteractions(endpointRepository);
    }

    @Test
    void inactiveOwnerIsRejected() {
        owner.setActive(false);
        stubOwner();

        assertThrows(
                InactiveUserException.class,
                () -> service.register(ownerId, validRequest())
        );

        verifyNoInteractions(endpointRepository);
    }

    @Test
    void responseLimitAboveTimeoutIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.register(
                        ownerId,
                        new RegisterEndpointRequest(
                                "Health Check",
                                "https://example.com/health",
                                200, 60, 1000, 1001
                        )
                )
        );

        verifyNoInteractions(endpointRepository);
    }

    @Test
    void responseLimitEqualToTimeoutIsAccepted() {
        stubOwner();
        stubSave();

        EndpointResponse response = service.register(
                ownerId,
                new RegisterEndpointRequest(
                        "Health Check",
                        "https://example.com/health",
                        200, 60, 1000, 1000
                )
        );

        assertEquals(1000, response.timeoutMillis());
        assertEquals(1000, response.responseTimeLimitMillis());
    }

    @Test
    void unsupportedUrlIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.register(
                        ownerId,
                        new RegisterEndpointRequest(
                                "Health Check",
                                "file:///etc/passwd",
                                200, 60, 5000, 1000
                        )
                )
        );

        verifyNoInteractions(endpointRepository);
    }

    @Test
    void intervalBelowMinimumIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.register(
                        ownerId,
                        new RegisterEndpointRequest(
                                "Health Check",
                                "https://example.com/health",
                                200, 59, 5000, 1000
                        )
                )
        );

        verifyNoInteractions(endpointRepository);
    }

    private void stubOwner() {
        when(userRepository.findById(ownerId))
                .thenReturn(Optional.of(owner));
    }

    private void stubSave() {
        when(endpointRepository.save(any(MonitoredEndpoint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RegisterEndpointRequest validRequest() {
        return new RegisterEndpointRequest(
                "Health Check",
                "https://example.com/health",
                200,
                60,
                5000,
                1000
        );
    }
}