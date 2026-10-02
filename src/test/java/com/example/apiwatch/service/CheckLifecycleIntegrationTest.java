package com.example.apiwatch.service;

import com.example.apiwatch.dto.CheckResultResponse;
import com.example.apiwatch.dto.ClaimedEndpointCheck;
import com.example.apiwatch.dto.HttpProbeResult;
import com.example.apiwatch.entity.Alert;
import com.example.apiwatch.entity.MonitoredEndpoint;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.enums.EndpointStatus;
import com.example.apiwatch.repository.AlertRepository;
import com.example.apiwatch.repository.MonitoredEndpointRepository;
import com.example.apiwatch.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5441/apiwatch_test_db",
        "spring.datasource.username=apiwatch_test_user",
        "spring.datasource.password=apiwatch_test_password",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "apiwatch.monitoring.enabled=false",
        "apiwatch.alerts.enabled=false"
})
class CheckLifecycleIntegrationTest {

    @Autowired
    private CheckClaimService claimService;

    @Autowired
    private CheckRecordingService recordingService;

    @Autowired
    private EndpointManagementService managementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MonitoredEndpointRepository endpointRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AlertRepository alertRepository;

    private UUID ownerId;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.saveAndFlush(
                User.builder()
                        .fullName("Integration Test User")
                        .email(UUID.randomUUID() + "@example.com")
                        .passwordHash("test-only-password-hash")
                        .active(true)
                        .build()
        );

        ownerId = owner.getId();

        MonitoredEndpoint endpoint = endpointRepository.saveAndFlush(
                MonitoredEndpoint.builder()
                        .owner(owner)
                        .name("Integration Endpoint")
                        .url("https://example.com/health")
                        .nextCheckAt(Instant.now().minusSeconds(60))
                        .build()
        );

        endpointId = endpoint.getId();
    }

    @AfterEach
    void cleanUp() {
        if (endpointId != null) {
            jdbcTemplate.update(
                    "DELETE FROM alerts WHERE endpoint_id = ?",
                    endpointId
            );
            jdbcTemplate.update(
                    "DELETE FROM check_results WHERE endpoint_id = ?",
                    endpointId
            );
            jdbcTemplate.update(
                    "DELETE FROM monitored_endpoints WHERE id = ?",
                    endpointId
            );
        }

        if (ownerId != null) {
            jdbcTemplate.update(
                    "DELETE FROM app_users WHERE id = ?",
                    ownerId
            );
        }
    }

    @Test
    void concurrentClaimsProduceOnlyOneActiveClaim() throws Exception {
        List<Optional<ClaimedEndpointCheck>> results =
                runConcurrently(() -> claimService.claim(endpointId));

        assertEquals(
                1L,
                results.stream().filter(Optional::isPresent).count()
        );

        ClaimedEndpointCheck successfulClaim = results.stream()
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow();

        MonitoredEndpoint stored = storedEndpoint();

        assertEquals(
                successfulClaim.checkToken(),
                stored.getCheckToken()
        );
        assertNotNull(stored.getCheckLeaseUntil());
    }

    @Test
    void concurrentRecordingOfSameClaimCreatesOneCheck()
            throws Exception {
        ClaimedEndpointCheck claim = claimService.claim(endpointId)
                .orElseThrow();

        HttpProbeResult probe = new HttpProbeResult(200, 100, null);
        Instant checkedAt = Instant.now();

        List<Optional<CheckResultResponse>> results =
                runConcurrently(() -> recordingService.record(
                        endpointId,
                        claim.checkToken(),
                        probe,
                        checkedAt
                ));

        assertEquals(
                1L,
                results.stream().filter(Optional::isPresent).count()
        );
        assertEquals(1L, checkCount());
        assertEquals(0L, alertCount());

        MonitoredEndpoint stored = storedEndpoint();

        assertEquals(EndpointStatus.ONLINE, stored.getCurrentStatus());
        assertNull(stored.getCheckToken());
        assertNull(stored.getCheckLeaseUntil());
    }

    @Test
    void replacedClaimRejectsStaleResult() {
        ClaimedEndpointCheck oldClaim =
                claimService.claim(endpointId).orElseThrow();

        MonitoredEndpoint endpoint = storedEndpoint();
        endpoint.setCheckLeaseUntil(Instant.now().minusSeconds(1));
        endpointRepository.saveAndFlush(endpoint);

        ClaimedEndpointCheck newClaim =
                claimService.claim(endpointId).orElseThrow();

        assertNotEquals(
                oldClaim.checkToken(),
                newClaim.checkToken()
        );

        Optional<CheckResultResponse> staleResult =
                recordingService.record(
                        endpointId,
                        oldClaim.checkToken(),
                        new HttpProbeResult(500, 100, null),
                        Instant.now()
                );

        assertTrue(staleResult.isEmpty());
        assertEquals(0L, checkCount());
        assertEquals(
                newClaim.checkToken(),
                storedEndpoint().getCheckToken()
        );

        Optional<CheckResultResponse> acceptedResult =
                recordingService.record(
                        endpointId,
                        newClaim.checkToken(),
                        new HttpProbeResult(200, 100, null),
                        Instant.now()
                );

        assertTrue(acceptedResult.isPresent());
        assertEquals(1L, checkCount());
    }

    @Test
    void pausingInvalidatesClaimAndRejectsResultWhilePaused() {
        ClaimedEndpointCheck oldClaim =
                claimService.claim(endpointId).orElseThrow();

        MonitoredEndpoint beforePause = storedEndpoint();
        Instant originalSchedule = beforePause.getNextCheckAt();

        managementService.updateMonitoringState(
                ownerId,
                endpointId,
                true
        );

        MonitoredEndpoint paused = storedEndpoint();

        assertAll(
                () -> assertTrue(paused.isPaused()),
                () -> assertNull(paused.getCheckToken()),
                () -> assertNull(paused.getCheckLeaseUntil()),
                () -> assertEquals(
                        originalSchedule,
                        paused.getNextCheckAt()
                ),
                () -> assertEquals(
                        EndpointStatus.PENDING,
                        paused.getCurrentStatus()
                )
        );

        assertTrue(claimService.claim(endpointId).isEmpty());

        Optional<CheckResultResponse> rejectedResult =
                recordingService.record(
                        endpointId,
                        oldClaim.checkToken(),
                        new HttpProbeResult(500, 100, null),
                        Instant.now()
                );

        MonitoredEndpoint stored = storedEndpoint();

        assertAll(
                () -> assertTrue(rejectedResult.isEmpty()),
                () -> assertEquals(0L, checkCount()),
                () -> assertEquals(0L, alertCount()),
                () -> assertTrue(stored.isPaused()),
                () -> assertEquals(
                        EndpointStatus.PENDING,
                        stored.getCurrentStatus()
                ),
                () -> assertEquals(0, stored.getConsecutiveFailures()),
                () -> assertFalse(stored.isOutageOpen()),
                () -> assertNull(stored.getLastCheckedAt())
        );
    }

    @Test
    void pauseThenResumeRejectsOldResultAndAcceptsNewClaim() {
        // An old failed result would open an outage if accepted.
        MonitoredEndpoint endpoint = storedEndpoint();
        endpoint.setConsecutiveFailures(2);
        endpointRepository.saveAndFlush(endpoint);

        ClaimedEndpointCheck oldClaim =
                claimService.claim(endpointId).orElseThrow();

        managementService.updateMonitoringState(
                ownerId,
                endpointId,
                true
        );

        managementService.updateMonitoringState(
                ownerId,
                endpointId,
                false
        );

        MonitoredEndpoint resumed = storedEndpoint();

        assertAll(
                () -> assertFalse(resumed.isPaused()),
                () -> assertNull(resumed.getCheckToken()),
                () -> assertNull(resumed.getCheckLeaseUntil()),
                () -> assertFalse(
                        resumed.getNextCheckAt().isAfter(Instant.now())
                ),
                () -> assertEquals(
                        2,
                        resumed.getConsecutiveFailures()
                )
        );

        // The old result must be rejected even before a new claim exists.
        Optional<CheckResultResponse> resultBeforeNewClaim =
                recordingService.record(
                        endpointId,
                        oldClaim.checkToken(),
                        new HttpProbeResult(500, 100, null),
                        Instant.now()
                );

        assertTrue(resultBeforeNewClaim.isEmpty());
        assertEquals(0L, checkCount());
        assertEquals(0L, alertCount());

        ClaimedEndpointCheck newClaim =
                claimService.claim(endpointId).orElseThrow();

        assertNotEquals(
                oldClaim.checkToken(),
                newClaim.checkToken()
        );

        // The old result must also leave the new claim untouched.
        Optional<CheckResultResponse> resultAfterNewClaim =
                recordingService.record(
                        endpointId,
                        oldClaim.checkToken(),
                        new HttpProbeResult(500, 100, null),
                        Instant.now()
                );

        MonitoredEndpoint afterRejectedResult = storedEndpoint();

        assertAll(
                () -> assertTrue(resultAfterNewClaim.isEmpty()),
                () -> assertEquals(0L, checkCount()),
                () -> assertEquals(0L, alertCount()),
                () -> assertEquals(
                        newClaim.checkToken(),
                        afterRejectedResult.getCheckToken()
                ),
                () -> assertNotNull(
                        afterRejectedResult.getCheckLeaseUntil()
                ),
                () -> assertEquals(
                        EndpointStatus.PENDING,
                        afterRejectedResult.getCurrentStatus()
                ),
                () -> assertEquals(
                        2,
                        afterRejectedResult.getConsecutiveFailures()
                ),
                () -> assertFalse(afterRejectedResult.isOutageOpen()),
                () -> assertNull(afterRejectedResult.getLastCheckedAt())
        );

        Instant checkedAt = Instant.now();

        CheckResultResponse acceptedResult = recordingService.record(
                endpointId,
                newClaim.checkToken(),
                new HttpProbeResult(200, 100, null),
                checkedAt
        ).orElseThrow();

        MonitoredEndpoint afterAcceptedResult = storedEndpoint();

        assertAll(
                () -> assertEquals(
                        EndpointStatus.ONLINE,
                        acceptedResult.status()
                ),
                () -> assertEquals(1L, checkCount()),
                () -> assertEquals(0L, alertCount()),
                () -> assertFalse(afterAcceptedResult.isPaused()),
                () -> assertEquals(
                        EndpointStatus.ONLINE,
                        afterAcceptedResult.getCurrentStatus()
                ),
                () -> assertEquals(
                        0,
                        afterAcceptedResult.getConsecutiveFailures()
                ),
                () -> assertFalse(afterAcceptedResult.isOutageOpen()),
                () -> assertNotNull(
                        afterAcceptedResult.getLastCheckedAt()
                ),
                () -> assertTrue(
                        afterAcceptedResult.getNextCheckAt()
                                .isAfter(checkedAt)
                ),
                () -> assertNull(afterAcceptedResult.getCheckToken()),
                () -> assertNull(
                        afterAcceptedResult.getCheckLeaseUntil()
                )
        );
    }

    @Test
    void alertSaveFailureRollsBackCheckAndEndpointChanges() {
        MonitoredEndpoint endpoint = storedEndpoint();
        endpoint.setConsecutiveFailures(2);
        endpointRepository.saveAndFlush(endpoint);

        ClaimedEndpointCheck claim =
                claimService.claim(endpointId).orElseThrow();

        doThrow(new DataIntegrityViolationException(
                "Simulated alert persistence failure"
        )).when(alertRepository).save(any(Alert.class));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> recordingService.record(
                        endpointId,
                        claim.checkToken(),
                        new HttpProbeResult(500, 100, null),
                        Instant.now()
                )
        );

        MonitoredEndpoint stored = storedEndpoint();

        assertAll(
                () -> assertEquals(0L, checkCount()),
                () -> assertEquals(0L, alertCount()),
                () -> assertEquals(
                        EndpointStatus.PENDING,
                        stored.getCurrentStatus()
                ),
                () -> assertEquals(2, stored.getConsecutiveFailures()),
                () -> assertFalse(stored.isOutageOpen()),
                () -> assertNull(stored.getLastCheckedAt()),
                () -> assertEquals(
                        claim.checkToken(),
                        stored.getCheckToken()
                ),
                () -> assertNotNull(stored.getCheckLeaseUntil())
        );
    }

    private MonitoredEndpoint storedEndpoint() {
        return endpointRepository.findById(endpointId).orElseThrow();
    }

    private long checkCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM check_results WHERE endpoint_id = ?",
                Long.class,
                endpointId
        );
    }

    private long alertCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alerts WHERE endpoint_id = ?",
                Long.class,
                endpointId
        );
    }

    private <T> List<T> runConcurrently(Callable<T> operation)
            throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<T> task = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start barrier timed out");
            }

            return operation.call();
        };

        try {
            Future<T> first = workers.submit(task);
            Future<T> second = workers.submit(task);

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both workers must reach the start barrier"
            );

            start.countDown();

            return List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }
}