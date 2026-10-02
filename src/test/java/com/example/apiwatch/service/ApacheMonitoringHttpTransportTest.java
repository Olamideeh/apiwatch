package com.example.apiwatch.service;

import com.example.apiwatch.config.MonitoringHttpClientConfig;
import com.example.apiwatch.security.PublicDnsResolver;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApacheMonitoringHttpTransportTest {

    private HttpServer server;
    private CloseableHttpClient client;
    private ApacheMonitoringHttpTransport transport;
    private PublicHostResolver hostResolver;
    private URI baseUri;

    private final AtomicInteger receivedRequests = new AtomicInteger();
    private final AtomicInteger redirectTargetRequests =
            new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/ok", exchange -> {
            receivedRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        server.createContext("/failure", exchange -> {
            receivedRequests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        server.createContext("/redirect", exchange -> {
            receivedRequests.incrementAndGet();
            exchange.getResponseHeaders().set("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.createContext("/target", exchange -> {
            redirectTargetRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        server.start();

        baseUri = URI.create(
                "http://monitor.example:"
                        + server.getAddress().getPort()
        );

        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        // Test-only DNS mapping to the controlled local server.
        // Production still uses PublicAddressPolicy.
        PublicDnsResolver dnsResolver = mock(PublicDnsResolver.class);

        when(dnsResolver.resolve("monitor.example"))
                .thenReturn(new InetAddress[]{loopback});

        when(dnsResolver.resolveCanonicalHostname("monitor.example"))
                .thenReturn("monitor.example");

        client = new MonitoringHttpClientConfig()
                .monitoringHttpClient(dnsResolver);

        hostResolver = mock(PublicHostResolver.class);

        when(hostResolver.resolve("monitor.example"))
                .thenReturn(new InetAddress[]{loopback});

        transport = new ApacheMonitoringHttpTransport(
                client,
                hostResolver,
                new EndpointUrlValidator()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    @Test
    void readsSuccessfulHttpStatus() throws Exception {
        int status = transport.getStatusCode(
                baseUri.resolve("/ok"),
                2000
        );

        assertEquals(200, status);
        assertEquals(1, receivedRequests.get());

        verify(hostResolver).resolve("monitor.example");
    }

    @Test
    void returnsErrorStatusWithoutThrowing() throws Exception {
        int status = transport.getStatusCode(
                baseUri.resolve("/failure"),
                2000
        );

        assertEquals(500, status);
        assertEquals(1, receivedRequests.get());
    }

    @Test
    void doesNotFollowRedirect() throws Exception {
        int status = transport.getStatusCode(
                baseUri.resolve("/redirect"),
                2000
        );

        assertEquals(302, status);
        assertEquals(1, receivedRequests.get());
        assertEquals(0, redirectTargetRequests.get());
    }

    @Test
    void productionAddressPolicyBlocksBeforeSendingRequest()
            throws Exception {
        HostAddressResolver rawResolver =
                mock(HostAddressResolver.class);

        when(rawResolver.resolve("monitor.example"))
                .thenReturn(new InetAddress[]{
                        InetAddress.getByName("127.0.0.1")
                });

        PublicHostResolver protectedResolver = new PublicHostResolver(
                rawResolver,
                new PublicAddressPolicy()
        );

        ApacheMonitoringHttpTransport protectedTransport =
                new ApacheMonitoringHttpTransport(
                        client,
                        protectedResolver,
                        new EndpointUrlValidator()
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> protectedTransport.getStatusCode(
                        baseUri.resolve("/ok"),
                        2000
                )
        );

        assertEquals(0, receivedRequests.get());
    }

    @Test
    void invalidTimeoutIsRejectedBeforeResolution() {
        assertThrows(
                IllegalArgumentException.class,
                () -> transport.getStatusCode(
                        baseUri.resolve("/ok"),
                        99
                )
        );

        verifyNoInteractions(hostResolver);
        assertEquals(0, receivedRequests.get());
    }
}