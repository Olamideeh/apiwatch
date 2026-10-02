package com.example.apiwatch.service;

import lombok.RequiredArgsConstructor;
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

        // Reject blocked destinations before request execution.
        // The client's DNS resolver also validates connection-time resolution.
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

        try (ClassicHttpResponse response =
                     monitoringHttpClient.executeOpen(
                             null,
                             request,
                             HttpClientContext.create()
                     )) {
            int statusCode = response.getCode();

            // We only measure arrival of the response headers.
            // Cancel before closing to avoid downloading an arbitrary body.
            request.cancel();

            return statusCode;
        } finally {
            request.cancel();
        }
    }
}