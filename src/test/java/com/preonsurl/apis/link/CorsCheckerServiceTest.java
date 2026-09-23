package com.preonsurl.apis.link;

import com.preonsurl.apis.link.dto.CorsCheckResult;
import com.preonsurl.apis.link.service.CorsCheckerService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorsCheckerServiceTest {

    private static HttpServer server;
    private static int serverPort;
    private final CorsCheckerService corsCheckerService = new CorsCheckerService();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);

        // Allowed CORS endpoint
        server.createContext("/allowed-cors", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        // Specific allowed origin endpoint
        server.createContext("/specific-cors", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "https://myshortdomain.com");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        // Disallowed CORS endpoint (no Access-Control-Allow-Origin)
        server.createContext("/no-cors", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        server.start();
        serverPort = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Should detect allowed CORS with wildcard *")
    void shouldDetectAllowedCorsWildcard() {
        String url = "http://127.0.0.1:" + serverPort + "/allowed-cors";
        CorsCheckResult result = corsCheckerService.check(
                url,
                "https://anydomain.com",
                "GET",
                List.of("Content-Type")
        );

        assertNotNull(result);
        assertTrue(result.allowed());
        assertTrue(result.originAllowed());
        assertTrue(result.methodAllowed());
        assertTrue(result.headersAllowed());
        assertEquals("*", result.allowOrigin());
        assertEquals(200, result.status());
    }

    @Test
    @DisplayName("Should detect allowed CORS with exact origin match")
    void shouldDetectAllowedCorsExactOrigin() {
        String url = "http://127.0.0.1:" + serverPort + "/specific-cors";
        CorsCheckResult result = corsCheckerService.check(
                url,
                "https://myshortdomain.com",
                "GET",
                List.of()
        );

        assertNotNull(result);
        assertTrue(result.allowed());
        assertTrue(result.originAllowed());
        assertEquals("https://myshortdomain.com", result.allowOrigin());
    }

    @Test
    @DisplayName("Should detect disallowed CORS when origin does not match")
    void shouldDetectDisallowedCorsOriginMismatch() {
        String url = "http://127.0.0.1:" + serverPort + "/specific-cors";
        CorsCheckResult result = corsCheckerService.check(
                url,
                "https://different-domain.com",
                "GET",
                List.of()
        );

        assertNotNull(result);
        assertFalse(result.allowed());
        assertFalse(result.originAllowed());
    }

    @Test
    @DisplayName("Should detect disallowed CORS when headers are missing")
    void shouldDetectDisallowedCorsWhenMissingHeaders() {
        String url = "http://127.0.0.1:" + serverPort + "/no-cors";
        CorsCheckResult result = corsCheckerService.check(
                url,
                "https://example.com",
                "GET",
                List.of()
        );

        assertNotNull(result);
        assertFalse(result.allowed());
        assertFalse(result.originAllowed());
        assertNull(result.allowOrigin());
    }

    @Test
    @DisplayName("Should handle unreachable destination gracefully without throwing exception")
    void shouldHandleUnreachableDestinationGracefully() {
        CorsCheckResult result = corsCheckerService.check(
                "http://127.0.0.1:59999/xyz",
                "https://example.com",
                "GET",
                List.of()
        );

        assertNotNull(result);
        assertFalse(result.allowed());
        assertFalse(result.originAllowed());
        assertEquals(0, result.status());
        assertNotNull(result.message());
    }

    @Test
    @DisplayName("Should handle empty destination gracefully")
    void shouldHandleEmptyDestination() {
        CorsCheckResult result = corsCheckerService.check(
                "",
                "https://example.com",
                "GET",
                List.of()
        );

        assertNotNull(result);
        assertFalse(result.allowed());
        assertEquals("Destination URL is required", result.message());
    }
}
