package com.example.apiwatch.service;

import com.example.apiwatch.enums.AlertType;
import com.example.apiwatch.enums.EndpointStatus;
import org.junit.jupiter.api.Test;
import com.example.apiwatch.dto.MonitoringTransition;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringTransitionServiceTest {

    private final MonitoringTransitionService service =
            new MonitoringTransitionService();

    @Test
    void firstFailureDoesNotOpenOutage() {
        assertTransition(
                service.transition(0, false, EndpointStatus.OFFLINE),
                1, false, null
        );
    }

    @Test
    void secondFailureDoesNotOpenOutage() {
        assertTransition(
                service.transition(1, false, EndpointStatus.OFFLINE),
                2, false, null
        );
    }

    @Test
    void thirdFailureOpensOutageAndRequestsAlert() {
        assertTransition(
                service.transition(2, false, EndpointStatus.OFFLINE),
                3, true, AlertType.OUTAGE
        );
    }

    @Test
    void continuedFailureDoesNotRepeatOutageAlert() {
        assertTransition(
                service.transition(3, true, EndpointStatus.OFFLINE),
                4, true, null
        );
    }

    @Test
    void onlineResetsFailuresWithoutRecoveryWhenNoOutageExists() {
        assertTransition(
                service.transition(2, false, EndpointStatus.ONLINE),
                0, false, null
        );
    }

    @Test
    void onlineClosesOutageAndRequestsRecoveryAlert() {
        assertTransition(
                service.transition(4, true, EndpointStatus.ONLINE),
                0, false, AlertType.RECOVERY
        );
    }

    @Test
    void continuedOnlineDoesNotRepeatRecoveryAlert() {
        MonitoringTransition recovered =
                service.transition(3, true, EndpointStatus.ONLINE);

        assertTransition(
                service.transition(
                        recovered.consecutiveFailures(),
                        recovered.outageOpen(),
                        EndpointStatus.ONLINE
                ),
                0, false, null
        );
    }

    @Test
    void degradedResetsFailuresBeforeOutageOpens() {
        assertTransition(
                service.transition(2, false, EndpointStatus.DEGRADED),
                0, false, null
        );
    }

    @Test
    void degradedKeepsExistingOutageOpenWithoutRecoveryAlert() {
        assertTransition(
                service.transition(4, true, EndpointStatus.DEGRADED),
                0, true, null
        );
    }

    @Test
    void failureAfterDegradedDoesNotRepeatExistingOutageAlert() {
        assertTransition(
                service.transition(0, true, EndpointStatus.OFFLINE),
                1, true, null
        );
    }

    @Test
    void newOutageCanOpenAfterRecovery() {
        MonitoringTransition state =
                service.transition(3, true, EndpointStatus.ONLINE);

        for (int check = 0; check < 3; check++) {
            state = service.transition(
                    state.consecutiveFailures(),
                    state.outageOpen(),
                    EndpointStatus.OFFLINE
            );
        }

        assertTransition(state, 3, true, AlertType.OUTAGE);
    }

    @Test
    void failureCountDoesNotOverflow() {
        assertTransition(
                service.transition(
                        Integer.MAX_VALUE,
                        true,
                        EndpointStatus.OFFLINE
                ),
                Integer.MAX_VALUE, true, null
        );
    }

    @Test
    void negativeFailureCountIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(
                        -1, false, EndpointStatus.OFFLINE
                )
        );
    }

    @Test
    void pendingIsRejectedBecauseItIsNotACheckResult() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(
                        0, false, EndpointStatus.PENDING
                )
        );
    }

    @Test
    void nullStatusIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.transition(0, false, null)
        );
    }

    private void assertTransition(
            MonitoringTransition actual,
            int expectedFailures,
            boolean expectedOutageOpen,
            AlertType expectedAlert
    ) {
        assertAll(
                () -> assertEquals(
                        expectedFailures,
                        actual.consecutiveFailures()
                ),
                () -> assertEquals(
                        expectedOutageOpen,
                        actual.outageOpen()
                ),
                () -> assertEquals(
                        Optional.ofNullable(expectedAlert),
                        actual.alertType()
                )
        );
    }
}