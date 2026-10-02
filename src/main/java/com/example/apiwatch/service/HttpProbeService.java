package com.example.apiwatch.service;

import com.example.apiwatch.dto.HttpProbeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class HttpProbeService {

    private final EndpointUrlValidator urlValidator;
    private final MonitoringHttpTransport transport;

    public HttpProbeResult check(String url, int timeoutMillis) {
        if (timeoutMillis < 100 || timeoutMillis > 30000) {
            throw new IllegalArgumentException(
                    "Timeout must be between 100 and 30000 milliseconds"
            );
        }

        URI uri = urlValidator.validate(url);
        long startedAt = System.nanoTime();

        try {
            int statusCode = transport.getStatusCode(uri, timeoutMillis);

            return new HttpProbeResult(
                    statusCode,
                    elapsedMillis(startedAt),
                    null
            );
        } catch (IOException exception) {
            return new HttpProbeResult(
                    null,
                    elapsedMillis(startedAt),
                    "HTTP check failed: "
                            + exception.getClass().getSimpleName()
            );
        } catch (IllegalArgumentException exception) {
            return new HttpProbeResult(
                    null,
                    elapsedMillis(startedAt),
                    "Monitoring destination rejected"
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }
}