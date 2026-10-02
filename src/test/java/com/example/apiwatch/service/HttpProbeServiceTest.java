package com.example.apiwatch.service;

import com.example.apiwatch.dto.HttpProbeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpProbeServiceTest {

    private static final String URL = "https://example.com/health";
    private static final URI URI_VALUE = URI.create(URL);

    @Mock
    private MonitoringHttpTransport transport;

    private HttpProbeService service;

    @BeforeEach
    void setUp() {
        service = new HttpProbeService(
                new EndpointUrlValidator(),
                transport
        );
    }

    @Test
    void recordsSuccessfulResponse() throws Exception {
        when(transport.getStatusCode(URI_VALUE, 5000))
                .thenReturn(200);

        HttpProbeResult result = service.check(URL, 5000);

        assertEquals(Integer.valueOf(200), result.actualStatusCode());
        assertTrue(result.responseTimeMillis() >= 0);
        assertNull(result.errorMessage());

        verify(transport, times(1)).getStatusCode(URI_VALUE, 5000);
    }

    @Test
    void recordsErrorHttpStatusAsAResponse() throws Exception {
        when(transport.getStatusCode(URI_VALUE, 5000))
                .thenReturn(500);

        HttpProbeResult result = service.check(URL, 5000);

        assertEquals(Integer.valueOf(500), result.actualStatusCode());
        assertNull(result.errorMessage());
    }

    @Test
    void preservesRedirectStatusForClassification() throws Exception {
        when(transport.getStatusCode(URI_VALUE, 5000))
                .thenReturn(302);

        HttpProbeResult result = service.check(URL, 5000);

        assertEquals(Integer.valueOf(302), result.actualStatusCode());
        verify(transport, times(1)).getStatusCode(URI_VALUE, 5000);
    }

    @Test
    void timeoutProducesResultWithoutHttpStatus() throws Exception {
        when(transport.getStatusCode(URI_VALUE, 5000))
                .thenThrow(new SocketTimeoutException("Read timed out"));

        HttpProbeResult result = service.check(URL, 5000);

        assertNull(result.actualStatusCode());
        assertTrue(result.responseTimeMillis() >= 0);
        assertNotNull(result.errorMessage());

        verify(transport, times(1)).getStatusCode(URI_VALUE, 5000);
    }

    @Test
    void connectionFailureProducesResultWithoutHttpStatus()
            throws Exception {
        when(transport.getStatusCode(URI_VALUE, 5000))
                .thenThrow(new IOException("Connection failed"));

        HttpProbeResult result = service.check(URL, 5000);

        assertNull(result.actualStatusCode());
        assertNotNull(result.errorMessage());
    }

    @Test
    void blockedDestinationProducesFailureResult() throws Exception {
        when(transport.getStatusCode(URI_VALUE, 5000))
                .thenThrow(new IllegalArgumentException(
                        "Monitoring destination is blocked"
                ));

        HttpProbeResult result = service.check(URL, 5000);

        assertNull(result.actualStatusCode());
        assertNotNull(result.errorMessage());
    }

    @Test
    void invalidUrlIsRejectedBeforeTransportRuns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.check("file:///etc/passwd", 5000)
        );

        verifyNoInteractions(transport);
    }

    @Test
    void invalidTimeoutIsRejectedBeforeTransportRuns() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.check(URL, 99)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.check(URL, 30001)
                )
        );

        verifyNoInteractions(transport);
    }
}