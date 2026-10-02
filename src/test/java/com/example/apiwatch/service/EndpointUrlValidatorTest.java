package com.example.apiwatch.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class EndpointUrlValidatorTest {

    private final EndpointUrlValidator validator =
            new EndpointUrlValidator();

    @Test
    void acceptsHttpsUrl() {
        assertEquals(
                URI.create("https://example.com/health"),
                validator.validate("https://example.com/health")
        );
    }

    @Test
    void acceptsHttpUrl() {
        assertEquals(
                URI.create("http://example.com/health"),
                validator.validate("http://example.com/health")
        );
    }

    @Test
    void preservesPathQueryAndExplicitPort() {
        String url =
                "https://example.com:8443/api/health?region=ng";

        assertEquals(URI.create(url), validator.validate(url));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals(
                URI.create("https://example.com/health"),
                validator.validate("  https://example.com/health  ")
        );
    }

    @Test
    void acceptsCaseInsensitiveScheme() {
        URI result = validator.validate("HTTPS://example.com/health");

        assertEquals("example.com", result.getHost());
        assertEquals("/health", result.getPath());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingUrl(String url) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(url)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/file",
            "file:///etc/passwd",
            "mailto:user@example.com",
            "example.com/health",
            "/health",
            "//example.com/health",
            "https:///health",
            "https://",
            "https://example.com/a b",
            "https://user:password@example.com/health",
            "https://example.com/health#section",
            "https://example.com:0/health",
            "https://example.com:65536/health",
            "https://example.com:abc/health"
    })
    void rejectsInvalidOrUnsupportedUrl(String url) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(url)
        );
    }

    @Test
    void rejectsUrlLongerThan2048Characters() {
        String url = "https://example.com/" + "a".repeat(2048);

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(url)
        );
    }
}