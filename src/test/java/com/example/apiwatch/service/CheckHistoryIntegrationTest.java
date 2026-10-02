package com.example.apiwatch.service;

import com.example.apiwatch.dto.CheckResultResponse;
import com.example.apiwatch.dto.PageResponse;
import com.example.apiwatch.dto.UptimeResponse;
import com.example.apiwatch.entity.CheckResult;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.exception.ResourceNotFoundException;
import com.example.apiwatch.repository.CheckResultRepository;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
import com.example.apiwatch.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5441/apiwatch_test_db",
        "spring.datasource.username=apiwatch_test_user",
        "spring.datasource.password=apiwatch_test_password",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "apiwatch.monitoring.enabled=false"
})
@Transactional(isolation = Isolation.REPEATABLE_READ)
class CheckHistoryIntegrationTest {

    @Autowired
    private CheckHistoryService historyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MonitoredEndpointRepository endpointRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    private UUID ownerId;
    private UUID endpointId;
    private UUID otherEndpointId;

    private final Instant from =
            Instant.parse("2026-10-01T00:00:00Z");

    private final Instant to =
            Instant.parse("2026-10-02T00:00:00Z");

    @BeforeEach
    void setUp() {
        User owner = createUser();
        User otherOwner = createUser();

        MonitoredEndpoint endpoint = createEndpoint(owner);
        MonitoredEndpoint otherEndpoint = createEndpoint(otherOwner);

        ownerId = owner.getId();
        endpointId = endpoint.getId();
        otherEndpointId = otherEndpoint.getId();

        addCheck(endpoint, EndpointStatus.ONLINE, from.minusSeconds(1));
        addCheck(endpoint, EndpointStatus.ONLINE, from);
        addCheck(endpoint, EndpointStatus.DEGRADED, from.plusSeconds(60));
        addCheck(endpoint, EndpointStatus.OFFLINE, to.minusSeconds(1));
        addCheck(endpoint, EndpointStatus.ONLINE, to);

        addCheck(
                otherEndpoint,
                EndpointStatus.OFFLINE,
                from.plusSeconds(30)
        );

        checkResultRepository.flush();
    }

    @Test
    void uptimeIncludesStartAndExcludesEnd() {
        UptimeResponse response = historyService.getUptime(
                ownerId,
                endpointId,
                from,
                to
        );

        assertAll(
                () -> assertEquals(3L, response.totalChecks()),
                () -> assertEquals(2L, response.availableChecks()),
                () -> assertEquals(1L, response.offlineChecks()),
                () -> assertEquals(
                        new BigDecimal("66.67"),
                        response.uptimePercentage()
                )
        );
    }

    @Test
    void historyReturnsOnlyRequestedEndpointInDescendingTimeOrder() {
        PageResponse<CheckResultResponse> response =
                historyService.getHistory(
                        ownerId,
                        endpointId,
                        0,
                        20
                );

        assertEquals(5L, response.totalElements());
        assertEquals(5, response.content().size());
        assertEquals(to, response.content().getFirst().checkedAt());
        assertEquals(
                from.minusSeconds(1),
                response.content().getLast().checkedAt()
        );

        assertTrue(response.content().stream().allMatch(
                check -> endpointId.equals(check.endpointId())
        ));
    }

    @Test
    void anotherUsersHistoryIsNotAccessible() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> historyService.getHistory(
                        ownerId,
                        otherEndpointId,
                        0,
                        20
                )
        );
    }

    @Test
    void anotherUsersUptimeIsNotAccessible() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> historyService.getUptime(
                        ownerId,
                        otherEndpointId,
                        from,
                        to
                )
        );
    }

    private User createUser() {
        return userRepository.saveAndFlush(
                User.builder()
                        .fullName("History Test User")
                        .email(UUID.randomUUID() + "@example.com")
                        .passwordHash("test-only-password-hash")
                        .active(true)
                        .build()
        );
    }

    private MonitoredEndpoint createEndpoint(User owner) {
        return endpointRepository.saveAndFlush(
                MonitoredEndpoint.builder()
                        .owner(owner)
                        .name("History Test Endpoint")
                        .url("https://example.com/health")
                        .nextCheckAt(Instant.now())
                        .build()
        );
    }

    private void addCheck(
            MonitoredEndpoint endpoint,
            EndpointStatus status,
            Instant checkedAt
    ) {
        checkResultRepository.save(
                CheckResult.builder()
                        .endpoint(endpoint)
                        .status(status)
                        .actualStatusCode(
                                status == EndpointStatus.OFFLINE ? 500 : 200
                        )
                        .responseTimeMillis(
                                status == EndpointStatus.DEGRADED ? 1500 : 100
                        )
                        .checkedAt(checkedAt)
                        .build()
        );
    }
}