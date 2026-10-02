package com.example.apiwatch.service;

import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Service
public class PublicAddressPolicy {

    private static final int[][] BLOCKED_IPV4_RANGES = {
            {0, 0, 0, 0, 8},
            {10, 0, 0, 0, 8},
            {100, 64, 0, 0, 10},
            {127, 0, 0, 0, 8},
            {169, 254, 0, 0, 16},
            {172, 16, 0, 0, 12},
            {192, 0, 0, 0, 24},
            {192, 0, 2, 0, 24},
            {192, 88, 99, 0, 24},
            {192, 168, 0, 0, 16},
            {198, 18, 0, 0, 15},
            {198, 51, 100, 0, 24},
            {203, 0, 113, 0, 24},
            {224, 0, 0, 0, 4},
            {240, 0, 0, 0, 4}
    };

    public void requirePublicAddress(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException(
                    "Destination address is required"
            );
        }

        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            reject();
        }

        byte[] bytes = address.getAddress();

        if (bytes.length == 4) {
            validateIpv4(bytes);
        } else if (bytes.length == 16) {
            validateIpv6(bytes);
        } else {
            reject();
        }
    }

    private void validateIpv4(byte[] bytes) {
        for (int[] range : BLOCKED_IPV4_RANGES) {
            int[] prefix = {
                    range[0],
                    range[1],
                    range[2],
                    range[3]
            };

            if (matchesPrefix(bytes, prefix, range[4])) {
                reject();
            }
        }
    }

    private void validateIpv6(byte[] bytes) {
        // Conservatively allow only global-unicast space 2000::/3.
        if (!matchesPrefix(bytes, new int[]{0x20}, 3)) {
            reject();
        }

        // Special-purpose space, including transition mechanisms.
        if (matchesPrefix(bytes, new int[]{0x20, 0x01, 0x00}, 23)) {
            reject();
        }

        // Documentation range 2001:db8::/32.
        if (matchesPrefix(
                bytes,
                new int[]{0x20, 0x01, 0x0d, 0xb8},
                32
        )) {
            reject();
        }

        // 6to4 transition range 2002::/16.
        if (matchesPrefix(bytes, new int[]{0x20, 0x02}, 16)) {
            reject();
        }

        // Documentation range 3fff::/20.
        if (matchesPrefix(
                bytes,
                new int[]{0x3f, 0xff, 0x00},
                20
        )) {
            reject();
        }
    }

    private boolean matchesPrefix(
            byte[] address,
            int[] prefix,
            int prefixBits
    ) {
        int fullBytes = prefixBits / 8;
        int remainingBits = prefixBits % 8;

        for (int index = 0; index < fullBytes; index++) {
            if ((address[index] & 0xff) != prefix[index]) {
                return false;
            }
        }

        if (remainingBits == 0) {
            return true;
        }

        int mask = (0xff << (8 - remainingBits)) & 0xff;

        return ((address[fullBytes] & 0xff) & mask)
                == (prefix[fullBytes] & mask);
    }

    private void reject() {
        throw new IllegalArgumentException(
                "Monitoring destination must use an allowed public IP address"
        );
    }
}