package com.example.apiwatch.service;

import java.io.IOException;
import java.net.URI;

public interface MonitoringHttpTransport {

    int getStatusCode(URI uri, int timeoutMillis) throws IOException;
}