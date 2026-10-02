package com.example.apiwatch.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;

@Service
public class EndpointUrlValidator {

    public URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }

        String trimmedUrl = url.trim();

        if (trimmedUrl.length() > 2048) {
            throw new IllegalArgumentException(
                    "URL cannot exceed 2048 characters"
            );
        }

        URI uri;

        try {
            uri = new URI(trimmedUrl);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "URL is malformed",
                    exception
            );
        }

        String scheme = uri.getScheme();

        if (scheme == null
                || (!scheme.equalsIgnoreCase("http")
                && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(
                    "URL must use HTTP or HTTPS"
            );
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "URL must contain a valid host"
            );
        }

        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                    "URL cannot contain embedded credentials"
            );
        }

        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "URL cannot contain a fragment"
            );
        }

        int port = uri.getPort();

        if (port != -1 && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException(
                    "URL port must be between 1 and 65535"
            );
        }

        return uri;
    }
}