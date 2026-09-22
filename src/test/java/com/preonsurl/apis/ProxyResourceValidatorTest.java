package com.preonsurl.apis;

import com.preonsurl.apis.link.service.ProxyResourceValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class ProxyResourceValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "https://example.com/",
            "https://example.com/page",
            "https://example.com/products/123",
            "https://example.com/file.pdf",
            "https://example.com/archive.zip",
            "https://example.com/video.mp4",
            "https://example.com/download?id=123",
            "https://example.com/resource?id=123&type=download",
            "http://example.com"
    })
    void validUrls_passValidation(String url) {
        assertTrue(ProxyResourceValidator.isAcceptableProxyUrl(url), "URL should be accepted: " + url);
        assertDoesNotThrow(() -> ProxyResourceValidator.validateProxyUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/file",
            "file:///etc/passwd",
            "jar:file:/app.jar!/resource",
            "data:text/plain;base64,SGVsbG8=",
            "javascript:alert(1)"
    })
    void invalidSchemes_rejected(String url) {
        assertFalse(ProxyResourceValidator.isAcceptableProxyUrl(url));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ProxyResourceValidator.validateProxyUrl(url));
        assertTrue(ex.getMessage().contains("Unsupported scheme") || ex.getMessage().contains("Only 'http' and 'https'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://localhost:8080",
            "http://service.localhost",
            "http://internal-host.internal",
            "http://my-service.local",
            "http://metadata.google.internal/computeMetadata/v1/",
            "http://127.0.0.1",
            "http://127.0.0.1:8080/admin",
            "http://10.0.0.1",
            "http://10.254.0.1/secrets",
            "http://192.168.1.1",
            "http://172.16.0.1",
            "http://172.31.255.255",
            "http://169.254.169.254/latest/meta-data/",
            "http://169.254.1.1",
            "http://[::1]",
            "http://[::1]:8080"
    })
    void ssrfAndInternalDestinations_rejected(String url) {
        assertFalse(ProxyResourceValidator.isAcceptableProxyUrl(url), "Internal/SSRF URL should be rejected: " + url);
        assertThrows(IllegalArgumentException.class, () -> ProxyResourceValidator.validateProxyUrl(url));
    }

    @Test
    void userInfoInUri_rejected() {
        String url = "https://user:password@example.com/api";
        assertFalse(ProxyResourceValidator.isAcceptableProxyUrl(url));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ProxyResourceValidator.validateProxyUrl(url));
        assertTrue(ex.getMessage().contains("user credentials"));
    }

    @Test
    void nullOrEmptyUrl_rejected() {
        assertFalse(ProxyResourceValidator.isAcceptableProxyUrl(null));
        assertFalse(ProxyResourceValidator.isAcceptableProxyUrl("   "));
        assertThrows(IllegalArgumentException.class, () -> ProxyResourceValidator.validateProxyUrl(null));
        assertThrows(IllegalArgumentException.class, () -> ProxyResourceValidator.validateProxyUrl("   "));
    }

    @Test
    void malformedUri_rejected() {
        String url = "http://bad host with spaces/";
        assertFalse(ProxyResourceValidator.isAcceptableProxyUrl(url));
        assertThrows(IllegalArgumentException.class, () -> ProxyResourceValidator.validateProxyUrl(url));
    }

    @Test
    void isSafeAddress_validatesKnownRanges() throws Exception {
        // Loopback & Unspecified
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("127.255.255.255")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("0.0.0.0")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("::1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("::")));

        // RFC 1918 Private Ranges
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("10.0.0.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("172.16.0.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("172.31.255.255")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("192.168.0.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("192.168.254.254")));

        // Link-Local / Cloud Metadata
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("169.254.169.254")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("169.254.1.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("fe80::1")));

        // CGNAT & Documentation
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("100.64.0.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("192.0.2.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("198.51.100.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("203.0.113.1")));

        // Multicast & Reserved
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("224.0.0.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("240.0.0.1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("255.255.255.255")));

        // IPv6 Unique Local
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("fc00::1")));
        assertFalse(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("fd00::1")));

        // Public IP (e.g. 93.184.216.34 - example.com or 8.8.8.8 - Google DNS)
        assertTrue(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("93.184.216.34")));
        assertTrue(ProxyResourceValidator.isSafeAddress(InetAddress.getByName("1.1.1.1")));
    }
}
