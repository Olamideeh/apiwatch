package com.example.apiwatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApacheMonitoringHttpTransport
        implements MonitoringHttpTransport {

    private final CloseableHttpClient monitoringHttpClient;
    private final PublicHostResolver publicHostResolver;
    private final EndpointUrlValidator urlValidator;

    @Override
    @SuppressWarnings("deprecation")
    public int getStatusCode(
            URI uri,
            int timeoutMillis
    ) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URL is required");
        }

        if (timeoutMillis < 100 || timeoutMillis > 30000) {
            throw new IllegalArgumentException(
                    "Timeout must be between 100 and 30000 milliseconds"
            );
        }

        URI validatedUri = urlValidator.validate(uri.toString());

        publicHostResolver.resolve(validatedUri.getHost());

        Timeout timeout = Timeout.ofMilliseconds(timeoutMillis);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(timeout)
                .setConnectTimeout(timeout)
                .setResponseTimeout(timeout)
                .setRedirectsEnabled(false)
                .setAuthenticationEnabled(false)
                .build();

        HttpGet request = new HttpGet(validatedUri);
        request.setConfig(requestConfig);
        request.setHeader("User-Agent", "APIWatch/1.0");

        ClassicHttpResponse response = null;

        try {
            response = monitoringHttpClient.executeOpen(
                    null,
                    request,
                    HttpClientContext.create()
            );

            // A completed check measures arrival of the response headers.
            return response.getCode();
        } finally {
            // Stop downloading the body and discard this connection.
            request.cancel();

            if (response != null) {
                try {
                    response.close();
                } catch (IOException cleanupException) {
                    // Cleanup must not replace an already received status.
                    log.debug(
                            "Response cleanup failed for host {}",
                            validatedUri.getHost(),
                            cleanupException
                    );
                }
            }
        }
    }
}