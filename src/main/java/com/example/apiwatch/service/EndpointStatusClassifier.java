package com.example.apiwatch.service;

import com.example.apiwatch.enums.EndpointStatus;
import org.springframework.stereotype.Service;

@Service
public class EndpointStatusClassifier {

    public EndpointStatus classify(
            int expectedStatusCode,
            Integer actualStatusCode,
            long responseTimeMillis,
            int responseTimeLimitMillis
    ) {
        validateStatusCode(expectedStatusCode, "Expected");

        if (actualStatusCode != null) {
            validateStatusCode(actualStatusCode, "Actual");
        }

        if (responseTimeMillis < 0) {
            throw new IllegalArgumentException(
                    "Response time cannot be negative"
            );
        }

        if (responseTimeLimitMillis <= 0) {
            throw new IllegalArgumentException(
                    "Response time limit must be positive"
            );
        }

        if (actualStatusCode == null
                || actualStatusCode.intValue() != expectedStatusCode) {
            return EndpointStatus.OFFLINE;
        }

        if (responseTimeMillis > responseTimeLimitMillis) {
            return EndpointStatus.DEGRADED;
        }

        return EndpointStatus.ONLINE;
    }

    private void validateStatusCode(int statusCode, String label) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException(
                    label + " HTTP status code must be between 100 and 599"
            );
        }
    }
}