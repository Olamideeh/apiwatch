package com.example.apiwatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Service
@RequiredArgsConstructor
public class PublicHostResolver {

    private final HostAddressResolver addressResolver;
    private final PublicAddressPolicy addressPolicy;

    public InetAddress[] resolve(String host) throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(
                    "Destination host is required"
            );
        }

        InetAddress[] resolved = addressResolver.resolve(host);

        if (resolved == null || resolved.length == 0) {
            throw new UnknownHostException(
                    "No addresses returned for destination host"
            );
        }

        InetAddress[] validated = resolved.clone();

        for (InetAddress address : validated) {
            addressPolicy.requirePublicAddress(address);
        }

        return validated;
    }
}