package com.example.apiwatch.service;

import com.example.apiwatch.dto.EndpointResponse;
import com.example.apiwatch.dto.PageResponse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MonitoredEndpointRepository endpointRepository;

    private EndpointManagementService service;
    private User owner;
    private MonitoredEndpoint endpoint;
    private UUID ownerId;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        endpointId = UUID.randomUUID();

        owner = User.builder()
                .id(ownerId)
                .email("user@example.com")
                .active(true)
                .build();

        endpoint = MonitoredEndpoint.builder()
                .id(endpointId)
                .owner(owner)
                .name("Health Check")
                .url("https://example.com/health")
                .currentStatus(EndpointStatus.OFFLINE)
                .consecutiveFailures(3)
                .outageOpen(true)
                .nextCheckAt(Instant.parse("2099-01-01T00:00:00Z"))
                .lastCheckedAt(Instant.parse("2026-10-02T03:00:00Z"))
                .createdAt(Instant.parse("2026-10-02T02:00:00Z"))
                .build();

        service = new EndpointManagementService(
                userRepository,
                endpointRepository
        );
    }

    @Test
    void retrievesEndpointUsingOwnerScopedLookup() {
        stubOwner();
        stubEndpoint();

        EndpointResponse response =
                service.getEndpoint(ownerId, endpointId);

        assertEquals(endpointId, response.id());
        assertEquals(EndpointStatus.OFFLINE, response.currentStatus());

        verify(endpointRepository)
                .findByIdAndOwner_Id(endpointId, ownerId);

        verify(endpointRepository, never()).findById(any(UUID.class));
        verify(endpointRepository, never())
                .findByIdAndOwnerIdForUpdate(any(), any());
    }

    @Test
    void unavailableEndpointReturnsNotFound() {
        stubOwner();

        when(endpointRepository.findByIdAndOwner_Id(
                endpointId, ownerId
        )).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.getEndpoint(ownerId, endpointId)
        );
    }

    @Test
    void listsOnlyOwnerEndpointsWithStableSorting() {
        stubOwner();

        when(endpointRepository.findByOwner_Id(
                eq(ownerId), any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(endpoint),
                PageRequest.of(0, 20),
                1
        ));

        PageResponse<EndpointResponse> response =
                service.listEndpoints(ownerId, 0, 20);

        assertAll(
                () -> assertEquals(1, response.content().size()),
                () -> assertEquals(
                        endpointId, response.content().getFirst().id()
                ),
                () -> assertEquals(0, response.page()),
                () -> assertEquals(20, response.size()),
                () -> assertEquals(1L, response.totalElements()),
                () -> assertEquals(1, response.totalPages()),
                () -> assertTrue(response.last())
        );

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(endpointRepository)
                .findByOwner_Id(eq(ownerId), captor.capture());

        assertEquals(
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                ),
                captor.getValue().getSort()
        );
    }

    @Test
    void emptyListReturnsEmptyPage() {
        stubOwner();

        when(endpointRepository.findByOwner_Id(
                eq(ownerId), any(Pageable.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        PageResponse<EndpointResponse> response =
                service.listEndpoints(ownerId, 0, 20);

        assertTrue(response.content().isEmpty());
        assertEquals(0L, response.totalElements());
    }

    @ParameterizedTest
    @CsvSource({
            "-1,20",
            "0,0",
            "0,101"
    })
    void invalidPaginationIsRejected(int page, int size) {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listEndpoints(ownerId, page, size)
        );

        verifyNoInteractions(userRepository, endpointRepository);
    }

    @Test
    void pausingPreservesStatusAndScheduleAndInvalidatesActiveCheck() {
        UUID previousToken = UUID.randomUUID();
        endpoint.setCheckToken(previousToken);
        endpoint.setCheckLeaseUntil(
                Instant.parse("2099-01-01T00:02:00Z")
        );

        stubOwner();
        stubLockedEndpoint();
        stubSave();

        Instant originalSchedule = endpoint.getNextCheckAt();
        Instant originalLastCheck = endpoint.getLastCheckedAt();

        EndpointResponse response = service.updateMonitoringState(
                ownerId, endpointId, true
        );

        assertAll(
                () -> assertTrue(response.paused()),
                () -> assertEquals(
                        originalSchedule, response.nextCheckAt()
                ),
                () -> assertEquals(
                        originalLastCheck, response.lastCheckedAt()
                ),
                () -> assertEquals(
                        EndpointStatus.OFFLINE, response.currentStatus()
                ),
                () -> assertEquals(3, response.consecutiveFailures()),
                () -> assertTrue(endpoint.isOutageOpen()),
                () -> assertNull(endpoint.getCheckToken()),
                () -> assertNull(endpoint.getCheckLeaseUntil())
        );

        verify(endpointRepository)
                .findByIdAndOwnerIdForUpdate(endpointId, ownerId);

        verify(endpointRepository, never())
                .findByIdAndOwner_Id(any(), any());
    }

    @Test
    void resumingMakesEndpointDueImmediatelyAndClearsPreviousClaim() {
        endpoint.setPaused(true);
        endpoint.setCheckToken(UUID.randomUUID());
        endpoint.setCheckLeaseUntil(
                Instant.parse("2099-01-01T00:02:00Z")
        );

        stubOwner();
        stubLockedEndpoint();
        stubSave();

        Instant before = Instant.now();

        EndpointResponse response = service.updateMonitoringState(
                ownerId, endpointId, false
        );

        Instant after = Instant.now();

        assertAll(
                () -> assertFalse(response.paused()),
                () -> assertFalse(response.nextCheckAt().isBefore(before)),
                () -> assertFalse(response.nextCheckAt().isAfter(after)),
                () -> assertEquals(
                        EndpointStatus.OFFLINE, response.currentStatus()
                ),
                () -> assertEquals(3, response.consecutiveFailures()),
                () -> assertTrue(endpoint.isOutageOpen()),
                () -> assertEquals(
                        Instant.parse("2026-10-02T03:00:00Z"),
                        response.lastCheckedAt()
                ),
                () -> assertNull(endpoint.getCheckToken()),
                () -> assertNull(endpoint.getCheckLeaseUntil())
        );
    }

    @Test
    void pauseThenResumeDoesNotRestorePreviousCheckToken() {
        UUID previousToken = UUID.randomUUID();

        endpoint.setCheckToken(previousToken);
        endpoint.setCheckLeaseUntil(
                Instant.parse("2099-01-01T00:02:00Z")
        );

        stubOwner();
        stubLockedEndpoint();
        stubSave();

        service.updateMonitoringState(ownerId, endpointId, true);
        service.updateMonitoringState(ownerId, endpointId, false);

        assertAll(
                () -> assertFalse(endpoint.isPaused()),
                () -> assertNull(endpoint.getCheckToken()),
                () -> assertNull(endpoint.getCheckLeaseUntil()),
                () -> assertEquals(
                        EndpointStatus.OFFLINE,
                        endpoint.getCurrentStatus()
                ),
                () -> assertEquals(3, endpoint.getConsecutiveFailures()),
                () -> assertTrue(endpoint.isOutageOpen())
        );

        verify(endpointRepository, times(2))
                .findByIdAndOwnerIdForUpdate(endpointId, ownerId);

        verify(endpointRepository, times(2)).save(endpoint);
    }

    @Test
    void repeatingPausedStateDoesNotSaveOrChangeSchedule() {
        endpoint.setPaused(true);

        stubOwner();
        stubLockedEndpoint();

        Instant originalSchedule = endpoint.getNextCheckAt();

        EndpointResponse response = service.updateMonitoringState(
                ownerId, endpointId, true
        );

        assertTrue(response.paused());
        assertEquals(originalSchedule, response.nextCheckAt());

        verify(endpointRepository, never())
                .save(any(MonitoredEndpoint.class));
    }

    @Test
    void repeatingRunningStatePreservesActiveCheckAndSchedule() {
        UUID token = UUID.randomUUID();
        Instant lease = Instant.parse("2099-01-01T00:02:00Z");

        endpoint.setCheckToken(token);
        endpoint.setCheckLeaseUntil(lease);

        stubOwner();
        stubLockedEndpoint();

        Instant originalSchedule = endpoint.getNextCheckAt();

        EndpointResponse response = service.updateMonitoringState(
                ownerId, endpointId, false
        );

        assertAll(
                () -> assertFalse(response.paused()),
                () -> assertEquals(
                        originalSchedule, response.nextCheckAt()
                ),
                () -> assertEquals(token, endpoint.getCheckToken()),
                () -> assertEquals(lease, endpoint.getCheckLeaseUntil())
        );

        verify(endpointRepository, never())
                .save(any(MonitoredEndpoint.class));
    }

    @Test
    void cannotUpdateUnavailableEndpoint() {
        stubOwner();

        when(endpointRepository.findByIdAndOwnerIdForUpdate(
                endpointId, ownerId
        )).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.updateMonitoringState(
                        ownerId, endpointId, true
                )
        );

        verify(endpointRepository, never())
                .save(any(MonitoredEndpoint.class));

        verify(endpointRepository, never())
                .findByIdForUpdate(any());
    }

    @Test
    void inactiveOwnerCannotUseManagementOperations() {
        owner.setActive(false);
        stubOwner();

        assertAll(
                () -> assertThrows(
                        InactiveUserException.class,
                        () -> service.getEndpoint(ownerId, endpointId)
                ),
                () -> assertThrows(
                        InactiveUserException.class,
                        () -> service.listEndpoints(ownerId, 0, 20)
                ),
                () -> assertThrows(
                        InactiveUserException.class,
                        () -> service.updateMonitoringState(
                                ownerId, endpointId, true
                        )
                )
        );

        verifyNoInteractions(endpointRepository);
    }

    @Test
    void unknownOwnerIsRejected() {
        when(userRepository.findById(ownerId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.getEndpoint(ownerId, endpointId)
        );

        verifyNoInteractions(endpointRepository);
    }

    @Test
    void missingOwnerIdIsRejected() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getEndpoint(null, endpointId)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.listEndpoints(null, 0, 20)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.updateMonitoringState(
                                null, endpointId, true
                        )
                )
        );

        verifyNoInteractions(userRepository, endpointRepository);
    }

    @Test
    void missingEndpointIdIsRejectedBeforeEndpointLookup() {
        stubOwner();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getEndpoint(ownerId, null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.updateMonitoringState(
                                ownerId, null, true
                        )
                )
        );

        verifyNoInteractions(endpointRepository);
    }

    private void stubOwner() {
        when(userRepository.findById(ownerId))
                .thenReturn(Optional.of(owner));
    }

    private void stubEndpoint() {
        when(endpointRepository.findByIdAndOwner_Id(
                endpointId, ownerId
        )).thenReturn(Optional.of(endpoint));
    }

    private void stubLockedEndpoint() {
        when(endpointRepository.findByIdAndOwnerIdForUpdate(
                endpointId, ownerId
        )).thenReturn(Optional.of(endpoint));
    }

    private void stubSave() {
        when(endpointRepository.save(any(MonitoredEndpoint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}