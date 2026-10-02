package com.example.apiwatch.security;

import com.example.apiwatch.service.PublicHostResolver;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.DnsResolver;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
@RequiredArgsConstructor
public class PublicDnsResolver implements DnsResolver {

    private final PublicHostResolver publicHostResolver;

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return publicHostResolver.resolve(host);
    }

    @Override
    public String resolveCanonicalHostname(String host)
            throws UnknownHostException {
        publicHostResolver.resolve(host);
        return host;
    }
}