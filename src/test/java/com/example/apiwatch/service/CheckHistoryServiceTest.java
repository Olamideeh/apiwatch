package com.example.apiwatch.service;

import com.example.apiwatch.dto.CheckResultResponse;
import com.example.apiwatch.dto.PageResponse;
import com.example.apiwatch.dto.UptimeResponse;
import com.example.apiwatch.entity.CheckResult;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.exception.ResourceNotFoundException;
import com.example.apiwatch.repository.CheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckHistoryServiceTest {

    @Mock
    private EndpointManagementService managementService;

    @Mock
    private CheckResultRepository checkResultRepository;

    private CheckHistoryService service;
    private UUID ownerId;
    private UUID endpointId;

    private final Instant from =
            Instant.parse("2026-10-01T00:00:00Z");

    private final Instant to =
            Instant.parse("2026-10-02T00:00:00Z");

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        endpointId = UUID.randomUUID();

        service = new CheckHistoryService(
                managementService,
                checkResultRepository
        );
    }

    @Test
    void historyVerifiesOwnershipAndMapsResultsWithStableSorting() {
        UUID checkId = UUID.randomUUID();

        CheckResult result = CheckResult.builder()
                .id(checkId)
                .endpoint(MonitoredEndpoint.builder()
                        .id(endpointId)
                        .build())
                .status(EndpointStatus.ONLINE)
                .actualStatusCode(200)
                .responseTimeMillis(100)
                .checkedAt(from)
                .build();

        when(checkResultRepository.findByEndpoint_IdAndEndpoint_Owner_Id(
                eq(endpointId),
                eq(ownerId),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(result),
                PageRequest.of(0, 20),
                1
        ));

        PageResponse<CheckResultResponse> response =
                service.getHistory(ownerId, endpointId, 0, 20);

        CheckResultResponse check = response.content().getFirst();

        assertAll(
                () -> assertEquals(checkId, check.id()),
                () -> assertEquals(endpointId, check.endpointId()),
                () -> assertEquals(EndpointStatus.ONLINE, check.status()),
                () -> assertEquals(
                        Integer.valueOf(200), check.actualStatusCode()
                ),
                () -> assertEquals(100L, check.responseTimeMillis()),
                () -> assertEquals(from, check.checkedAt()),
                () -> assertEquals(1L, response.totalElements())
        );

        verify(managementService).getEndpoint(ownerId, endpointId);

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(checkResultRepository)
                .findByEndpoint_IdAndEndpoint_Owner_Id(
                        eq(endpointId),
                        eq(ownerId),
                        captor.capture()
                );

        assertEquals(
                Sort.by(
                        Sort.Order.desc("checkedAt"),
                        Sort.Order.desc("id")
                ),
                captor.getValue().getSort()
        );
    }

    @Test
    void historyWithoutChecksReturnsEmptyPage() {
        when(checkResultRepository.findByEndpoint_IdAndEndpoint_Owner_Id(
                eq(endpointId),
                eq(ownerId),
                any(Pageable.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        PageResponse<CheckResultResponse> response =
                service.getHistory(ownerId, endpointId, 0, 20);

        assertTrue(response.content().isEmpty());
        assertEquals(0L, response.totalElements());

        verify(managementService).getEndpoint(ownerId, endpointId);
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
                () -> service.getHistory(
                        ownerId, endpointId, page, size
                )
        );

        verifyNoInteractions(checkResultRepository);
    }

    @Test
    void unavailableEndpointCannotExposeHistory() {
        when(managementService.getEndpoint(ownerId, endpointId))
                .thenThrow(new ResourceNotFoundException(
                        "Endpoint not found"
                ));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.getHistory(ownerId, endpointId, 0, 20)
        );

        verifyNoInteractions(checkResultRepository);
    }

    @Test
    void uptimeCountsOnlineAndDegradedAsAvailableAndRounds() {
        stubCounts(3, 1);

        UptimeResponse response = service.getUptime(
                ownerId, endpointId, from, to
        );

        assertAll(
                () -> assertEquals(endpointId, response.endpointId()),
                () -> assertEquals(from, response.from()),
                () -> assertEquals(to, response.to()),
                () -> assertEquals(3L, response.totalChecks()),
                () -> assertEquals(2L, response.availableChecks()),
                () -> assertEquals(1L, response.offlineChecks()),
                () -> assertEquals(
                        new BigDecimal("66.67"),
                        response.uptimePercentage()
                )
        );

        verify(managementService).getEndpoint(ownerId, endpointId);
    }

    @Test
    void allAvailableChecksProduce100Percent() {
        stubCounts(10, 0);

        UptimeResponse response = service.getUptime(
                ownerId, endpointId, from, to
        );

        assertEquals(
                new BigDecimal("100.00"),
                response.uptimePercentage()
        );
    }

    @Test
    void allOfflineChecksProduceZeroPercent() {
        stubCounts(10, 10);

        UptimeResponse response = service.getUptime(
                ownerId, endpointId, from, to
        );

        assertEquals(0L, response.availableChecks());
        assertEquals(
                new BigDecimal("0.00"),
                response.uptimePercentage()
        );
    }

    @Test
    void noChecksProduceNullPercentage() {
        stubCounts(0, 0);

        UptimeResponse response = service.getUptime(
                ownerId, endpointId, from, to
        );

        assertEquals(0L, response.totalChecks());
        assertEquals(0L, response.availableChecks());
        assertEquals(0L, response.offlineChecks());
        assertNull(response.uptimePercentage());
    }

    @Test
    void invalidTimeRangesAreRejected() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getUptime(
                                ownerId, endpointId, null, to
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getUptime(
                                ownerId, endpointId, from, null
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getUptime(
                                ownerId, endpointId, from, from
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getUptime(
                                ownerId, endpointId, to, from
                        )
                )
        );

        verifyNoInteractions(checkResultRepository);
    }

    @Test
    void unavailableEndpointCannotExposeUptime() {
        when(managementService.getEndpoint(ownerId, endpointId))
                .thenThrow(new ResourceNotFoundException(
                        "Endpoint not found"
                ));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.getUptime(
                        ownerId, endpointId, from, to
                )
        );

        verifyNoInteractions(checkResultRepository);
    }

    private void stubCounts(long total, long offline) {
        when(checkResultRepository
                .countByEndpoint_IdAndEndpoint_Owner_IdAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
                        endpointId, ownerId, from, to
                )).thenReturn(total);

        when(checkResultRepository
                .countByEndpoint_IdAndEndpoint_Owner_IdAndStatusAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
                        endpointId,
                        ownerId,
                        EndpointStatus.OFFLINE,
                        from,
                        to
                )).thenReturn(offline);
    }
}