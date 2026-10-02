package com.example.apiwatch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicHostResolverTest {

    @Mock
    private HostAddressResolver addressResolver;

    private PublicHostResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PublicHostResolver(
                addressResolver,
                new PublicAddressPolicy()
        );
    }

    @Test
    void returnsValidatedIpv4AndIpv6Addresses() throws Exception {
        InetAddress[] addresses = {
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("2606:4700:4700::1111")
        };

        when(addressResolver.resolve("example.com"))
                .thenReturn(addresses);

        assertArrayEquals(addresses, resolver.resolve("example.com"));
    }

    @Test
    void rejectsPrivateDestination() throws Exception {
        when(addressResolver.resolve("example.com"))
                .thenReturn(new InetAddress[]{
                        InetAddress.getByName("10.0.0.1")
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("example.com")
        );
    }

    @Test
    void rejectsMixedPublicAndPrivateAddresses() throws Exception {
        when(addressResolver.resolve("example.com"))
                .thenReturn(new InetAddress[]{
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("127.0.0.1")
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("example.com")
        );
    }

    @Test
    void rejectsEmptyResolution() throws Exception {
        when(addressResolver.resolve("example.com"))
                .thenReturn(new InetAddress[0]);

        assertThrows(
                UnknownHostException.class,
                () -> resolver.resolve("example.com")
        );
    }

    @Test
    void propagatesDnsFailure() throws Exception {
        when(addressResolver.resolve("example.com"))
                .thenThrow(new UnknownHostException("DNS failed"));

        assertThrows(
                UnknownHostException.class,
                () -> resolver.resolve("example.com")
        );
    }

    @Test
    void rejectsMissingHostBeforeDnsLookup() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolve(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolve(" ")
                )
        );

        verifyNoInteractions(addressResolver);
    }

    @Test
    void validatesFreshResolutionOnEveryCall() throws Exception {
        when(addressResolver.resolve("example.com"))
                .thenReturn(
                        new InetAddress[]{
                                InetAddress.getByName("8.8.8.8")
                        },
                        new InetAddress[]{
                                InetAddress.getByName("169.254.169.254")
                        }
                );

        assertDoesNotThrow(() -> resolver.resolve("example.com"));

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("example.com")
        );

        verify(addressResolver, times(2)).resolve("example.com");
    }
}