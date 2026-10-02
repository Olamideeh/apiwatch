package com.example.apiwatch.service;

import com.example.apiwatch.dto.HttpProbeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
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
            int statusCode = transport.getStatusCode(
                    uri,
                    timeoutMillis
            );

            return new HttpProbeResult(
                    statusCode,
                    elapsedMillis(startedAt),
                    null
            );
        } catch (IOException exception) {
            log.debug(
                    "HTTP probe failed for host {}",
                    uri.getHost(),
                    exception
            );

            return new HttpProbeResult(
                    null,
                    elapsedMillis(startedAt),
                    "HTTP check failed: "
                            + exception.getClass().getSimpleName()
            );
        } catch (IllegalArgumentException exception) {
            log.debug(
                    "Monitoring destination rejected for host {}",
                    uri.getHost(),
                    exception
            );

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