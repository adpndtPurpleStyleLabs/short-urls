package com.preonsurl.apis.link.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates destination URLs for the PROXY mode.
 * Enforces allowed schemes (http, https), valid URI structure, no user credentials,
 * and comprehensive SSRF protection against internal, private, loopback, and metadata addresses.
 */
public final class ProxyResourceValidator {

    private static final Set<String> BLOCKED_HOST_SUFFIXES = Set.of(
            ".localhost",
            ".internal",
            ".local",
            ".corp",
            ".lan"
    );

    private static final Set<String> BLOCKED_EXACT_HOSTS = Set.of(
            "localhost",
            "metadata.google.internal",
            "metadata"
    );

    public static volatile boolean allowLoopbackForTesting = false;

    private ProxyResourceValidator() {
    }

    /**
     * Validates that the provided URL is a valid and safe destination for reverse proxying.
     * Throws {@link IllegalArgumentException} if invalid, unsupported, or unsafe.
     */
    public static void validateProxyUrl(String url) {
        validateAndNormalizeUri(url);
    }

    /**
     * Checks whether the provided URL is a valid and safe destination for reverse proxying.
     */
    public static boolean isAcceptableProxyUrl(String url) {
        try {
            validateProxyUrl(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parses, validates schemes, credentials, host, and performs SSRF resolution checks.
     * Returns the validated {@link URI}.
     */
    public static URI validateAndNormalizeUri(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Destination URL cannot be empty");
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid destination URL syntax: " + e.getMessage());
        }

        // 1. Allowed schemes: strictly http and https
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(
                    "Unsupported scheme '" + scheme + "'. Only 'http' and 'https' are allowed for proxy mode"
            );
        }

        // 2. Reject user-info (credentials in URL)
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new IllegalArgumentException("Destination URL must not contain user credentials");
        }

        // 3. Validate host
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Destination URL must contain a valid host");
        }

        // 4. Validate port if specified
        int port = uri.getPort();
        if (port != -1 && (port <= 0 || port > 65535)) {
            throw new IllegalArgumentException("Invalid port number in destination URL: " + port);
        }

        // 5. Hostname string checks
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (!allowLoopbackForTesting && BLOCKED_EXACT_HOSTS.contains(lowerHost)) {
            throw new IllegalArgumentException("Access to internal host '" + host + "' is forbidden");
        }
        if (!allowLoopbackForTesting) {
            for (String suffix : BLOCKED_HOST_SUFFIXES) {
                if (lowerHost.endsWith(suffix)) {
                    throw new IllegalArgumentException("Access to internal domain '" + host + "' is forbidden");
                }
            }
        }

        // 6. SSRF Protection: Resolve hostname and validate every IP address
        InetAddress[] resolvedAddresses;
        try {
            resolvedAddresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Destination host cannot be resolved: " + host);
        } catch (SecurityException e) {
            throw new IllegalArgumentException("Host resolution blocked by security manager: " + host);
        }

        if (resolvedAddresses == null || resolvedAddresses.length == 0) {
            throw new IllegalArgumentException("Destination host has no resolved IP addresses: " + host);
        }

        for (InetAddress address : resolvedAddresses) {
            if (!isSafeAddress(address)) {
                throw new IllegalArgumentException(
                        "Destination host '" + host + "' resolves to a private, loopback, or reserved IP: "
                                + address.getHostAddress()
                );
            }
        }

        return uri;
    }

    /**
     * Determines whether an IP address is a safe public routable address (not private, loopback, link-local, or reserved).
     */
    public static boolean isSafeAddress(InetAddress address) {
        if (address == null) {
            return false;
        }

        if (allowLoopbackForTesting && (address.isLoopbackAddress()
                || "127.0.0.1".equals(address.getHostAddress())
                || "::1".equals(address.getHostAddress())
                || "0:0:0:0:0:0:0:1".equals(address.getHostAddress()))) {
            return true;
        }

        // Standard Java address checks
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() ||
                address.isLinkLocalAddress() || address.isSiteLocalAddress() ||
                address.isMulticastAddress()) {
            return false;
        }

        byte[] rawBytes = address.getAddress();

        // Check IPv6 specific ranges
        if (address instanceof Inet6Address) {
            // IPv4-mapped IPv6 (::ffff:w.x.y.z) or IPv4-compatible IPv6 (::w.x.y.z)
            if (isIpv4MappedOrCompatible(rawBytes)) {
                byte[] ipv4Bytes = new byte[4];
                System.arraycopy(rawBytes, 12, ipv4Bytes, 0, 4);
                return isSafeIpv4(ipv4Bytes);
            }

            // IPv6 Unique Local Addresses (fc00::/7) -> first byte is 0xfc or 0xfd
            int firstByte = rawBytes[0] & 0xff;
            if ((firstByte & 0xfe) == 0xfc) {
                return false;
            }

            // IPv6 Link-Local (fe80::/10)
            if (firstByte == 0xfe && ((rawBytes[1] & 0xc0) == 0x80)) {
                return false;
            }

            // Discard prefix (100::/64)
            if (firstByte == 0x01 && rawBytes[1] == 0x00) {
                return false;
            }

            return true;
        }

        return isSafeIpv4(rawBytes);
    }

    private static boolean isSafeIpv4(byte[] bytes) {
        int b0 = bytes[0] & 0xff;
        int b1 = bytes[1] & 0xff;
        int b2 = bytes[2] & 0xff;
        int b3 = bytes[3] & 0xff;

        // 0.0.0.0/8 (Current network)
        if (b0 == 0) {
            return false;
        }

        // 10.0.0.0/8 (Private-Use RFC 1918)
        if (b0 == 10) {
            return false;
        }

        // 100.64.0.0/10 (Shared Address Space / CGNAT RFC 6598: 100.64.0.0 - 100.127.255.255)
        if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
            return false;
        }

        // 127.0.0.0/8 (Loopback RFC 1122)
        if (b0 == 127) {
            return false;
        }

        // 169.254.0.0/16 (Link Local RFC 3927) including cloud metadata 169.254.169.254
        if (b0 == 169 && b1 == 254) {
            return false;
        }

        // 172.16.0.0/12 (Private-Use RFC 1918: 172.16.0.0 - 172.31.255.255)
        if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
            return false;
        }

        // 192.0.0.0/24 (IETF Protocol Assignments RFC 6890)
        if (b0 == 192 && b1 == 0 && b2 == 0) {
            return false;
        }

        // 192.0.2.0/24 (TEST-NET-1 RFC 5737)
        if (b0 == 192 && b1 == 0 && b2 == 2) {
            return false;
        }

        // 192.168.0.0/16 (Private-Use RFC 1918)
        if (b0 == 192 && b1 == 168) {
            return false;
        }

        // 198.18.0.0/15 (Benchmarking RFC 2544: 198.18.0.0 - 198.19.255.255)
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return false;
        }

        // 198.51.100.0/24 (TEST-NET-2 RFC 5737)
        if (b0 == 198 && b1 == 51 && b2 == 100) {
            return false;
        }

        // 203.0.113.0/24 (TEST-NET-3 RFC 5737)
        if (b0 == 203 && b1 == 0 && b2 == 113) {
            return false;
        }

        // 224.0.0.0/4 (Multicast RFC 5771: 224.0.0.0 - 239.255.255.255)
        if (b0 >= 224 && b0 <= 239) {
            return false;
        }

        // 240.0.0.0/4 (Reserved for future use RFC 1112) and 255.255.255.255 (Broadcast)
        if (b0 >= 240) {
            return false;
        }

        return true;
    }

    private static boolean isIpv4MappedOrCompatible(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            return false;
        }
        boolean allZerosPrefix = true;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                allZerosPrefix = false;
                break;
            }
        }
        if (allZerosPrefix) {
            // ::ffff:w.x.y.z (IPv4 mapped)
            if (bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
                return true;
            }
            // ::w.x.y.z (IPv4 compatible)
            if (bytes[10] == 0 && bytes[11] == 0) {
                return true;
            }
        }
        return false;
    }
}
