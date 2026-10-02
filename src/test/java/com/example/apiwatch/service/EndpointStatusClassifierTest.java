package com.example.apiwatch.service;

import com.example.apiwatch.enums.EndpointStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndpointStatusClassifierTest {

    private final EndpointStatusClassifier classifier =
            new EndpointStatusClassifier();

    @Test
    void expectedStatusWithinLimitIsOnline() {
        assertEquals(
                EndpointStatus.ONLINE,
                classifier.classify(200, 200, 500, 1000)
        );
    }

    @Test
    void responseExactlyAtLimitIsOnline() {
        assertEquals(
                EndpointStatus.ONLINE,
                classifier.classify(200, 200, 1000, 1000)
        );
    }

    @Test
    void responseOneMillisecondAboveLimitIsDegraded() {
        assertEquals(
                EndpointStatus.DEGRADED,
                classifier.classify(200, 200, 1001, 1000)
        );
    }

    @Test
    void zeroMillisecondResponseIsOnline() {
        assertEquals(
                EndpointStatus.ONLINE,
                classifier.classify(200, 200, 0, 1000)
        );
    }

    @Test
    void unexpectedStatusIsOfflineEvenWhenFast() {
        assertEquals(
                EndpointStatus.OFFLINE,
                classifier.classify(200, 500, 100, 1000)
        );
    }

    @Test
    void unexpectedStatusIsOfflineEvenWhenSlow() {
        assertEquals(
                EndpointStatus.OFFLINE,
                classifier.classify(200, 500, 1500, 1000)
        );
    }

    @Test
    void missingHttpResponseIsOffline() {
        assertEquals(
                EndpointStatus.OFFLINE,
                classifier.classify(200, null, 5000, 1000)
        );
    }

    @Test
    void configuredExpectedStatusIsUsed() {
        assertEquals(
                EndpointStatus.ONLINE,
                classifier.classify(204, 204, 200, 1000)
        );
    }

    @Test
    void differentSuccessfulHttpStatusIsStillOffline() {
        assertEquals(
                EndpointStatus.OFFLINE,
                classifier.classify(200, 201, 200, 1000)
        );
    }

    @Test
    void explicitlyExpectedErrorStatusCanBeOnline() {
        assertEquals(
                EndpointStatus.ONLINE,
                classifier.classify(404, 404, 200, 1000)
        );
    }

    @Test
    void invalidExpectedStatusIsRejected() {
        for (int status : new int[]{99, 600}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> classifier.classify(status, 200, 100, 1000)
            );
        }
    }

    @Test
    void invalidActualStatusIsRejected() {
        for (int status : new int[]{99, 600}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> classifier.classify(200, status, 100, 1000)
            );
        }
    }

    @Test
    void negativeResponseTimeIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> classifier.classify(200, 200, -1, 1000)
        );
    }

    @Test
    void nonPositiveResponseTimeLimitIsRejected() {
        for (int limit : new int[]{0, -1}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> classifier.classify(200, 200, 100, limit)
            );
        }
    }
}