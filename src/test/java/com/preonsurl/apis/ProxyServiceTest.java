package com.preonsurl.apis;

import com.preonsurl.apis.link.service.ProxyResourceValidator;
import com.preonsurl.apis.link.service.ProxyService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyServiceTest {

    private ProxyService proxyService;
    private HttpServer server;
    private int port;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        proxyService = new ProxyService();
        ProxyResourceValidator.allowLoopbackForTesting = true;

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void tearDown() {
        ProxyResourceValidator.allowLoopbackForTesting = false;
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void proxy_htmlWebpage_streamsSuccessfully() throws Exception {
        String html = "<!DOCTYPE html><html><head><title>Test</title></head><body><h1>Hello World</h1></body></html>";
        server.createContext("/page", exchange -> {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0");

        ResponseEntity<?> response = proxyService.proxyRequest(baseUrl + "/page", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("text/html; charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Resource);
        String body = new String(((Resource) response.getBody()).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(html, body);
    }

    @Test
    void proxy_pdfFile_streamsWithContentDisposition() throws Exception {
        byte[] pdfBytes = "%PDF-1.4 dummy pdf bytes".getBytes(StandardCharsets.UTF_8);
        server.createContext("/doc.pdf", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"doc.pdf\"");
            exchange.sendResponseHeaders(200, pdfBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(pdfBytes);
            }
        });

        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = proxyService.proxyRequest(baseUrl + "/doc.pdf", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/pdf", response.getHeaders().getFirst("Content-Type"));
        assertEquals("attachment; filename=\"doc.pdf\"", response.getHeaders().getFirst("Content-Disposition"));
        assertTrue(response.getBody() instanceof Resource);
        byte[] body = ((Resource) response.getBody()).getInputStream().readAllBytes();
        assertEquals(new String(pdfBytes, StandardCharsets.UTF_8), new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void proxy_zipFile_streamsSuccessfully() throws Exception {
        byte[] zipBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x0A, 0x00, 0x00, 0x00};
        server.createContext("/archive.zip", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"archive.zip\"");
            exchange.sendResponseHeaders(200, zipBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(zipBytes);
            }
        });

        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = proxyService.proxyRequest(baseUrl + "/archive.zip", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/zip", response.getHeaders().getFirst("Content-Type"));
        assertTrue(response.getBody() instanceof Resource);
        assertEquals(zipBytes.length, ((Resource) response.getBody()).getInputStream().readAllBytes().length);
    }

    @Test
    void proxy_partialContent206_preservesStatusAndRangeHeaders() throws Exception {
        byte[] fullContent = "0123456789abcdefghij".getBytes(StandardCharsets.UTF_8);
        server.createContext("/video.mp4", exchange -> {
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            assertEquals("bytes=0-9", rangeHeader);

            exchange.getResponseHeaders().set("Content-Type", "video/mp4");
            exchange.getResponseHeaders().set("Content-Range", "bytes 0-9/20");
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.sendResponseHeaders(206, 10);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fullContent, 0, 10);
            }
        });

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Range", "bytes=0-9");

        ResponseEntity<?> response = proxyService.proxyRequest(baseUrl + "/video.mp4", request);

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals("video/mp4", response.getHeaders().getFirst("Content-Type"));
        assertEquals("bytes 0-9/20", response.getHeaders().getFirst("Content-Range"));
        assertEquals("bytes", response.getHeaders().getFirst("Accept-Ranges"));
        assertTrue(response.getBody() instanceof Resource);
        assertEquals("0123456789", new String(((Resource) response.getBody()).getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void proxy_safeRedirectFollowedSuccessfully() throws Exception {
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/final");
            exchange.sendResponseHeaders(302, -1);
        });

        server.createContext("/final", exchange -> {
            byte[] bytes = "redirect destination".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = proxyService.proxyRequest(baseUrl + "/start", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Resource);
        assertEquals("redirect destination", new String(((Resource) response.getBody()).getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void proxy_redirectLoopDetected_returns502BadGateway() throws Exception {
        server.createContext("/loop-a", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/loop-b");
            exchange.sendResponseHeaders(302, -1);
        });

        server.createContext("/loop-b", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/loop-a");
            exchange.sendResponseHeaders(302, -1);
        });

        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = proxyService.proxyRequest(baseUrl + "/loop-a", request);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }

    @Test
    void proxy_redirectToUnsafeAddress_blockedWith400BadRequest() throws Exception {
        server.createContext("/unsafe-redirect", exchange -> {
            // Redirect to private IP (10.0.0.1) which is blocked by SSRF validator even when allowLoopbackForTesting is true
            exchange.getResponseHeaders().set("Location", "http://10.0.0.1/admin");
            exchange.sendResponseHeaders(302, -1);
        });

        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = proxyService.proxyRequest(baseUrl + "/unsafe-redirect", request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void proxy_hopByHopHeadersStripped() throws Exception {
        server.createContext("/hop-by-hop", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.getResponseHeaders().set("Connection", "close");
            exchange.getResponseHeaders().set("Keep-Alive", "timeout=5");
            exchange.getResponseHeaders().set("Transfer-Encoding", "chunked");
            exchange.getResponseHeaders().set("X-Custom-Header", "allowed-val");
            byte[] bytes = "headers test".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = proxyService.proxyRequest(baseUrl + "/hop-by-hop", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getHeaders().getFirst("Connection"));
        assertNull(response.getHeaders().getFirst("Keep-Alive"));
        assertNull(response.getHeaders().getFirst("Transfer-Encoding"));
        // Custom headers not in forwarding allowlist should not be forwarded
        assertNull(response.getHeaders().getFirst("X-Custom-Header"));
    }

    @Test
    void proxy_userAgentForwardingAndFallback() throws Exception {
        AtomicReference<String> capturedUa1 = new AtomicReference<>();
        AtomicReference<String> capturedUa2 = new AtomicReference<>();

        server.createContext("/ua-client", exchange -> {
            capturedUa1.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        server.createContext("/ua-default", exchange -> {
            capturedUa2.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        // Test with client User-Agent
        MockHttpServletRequest requestWithUa = new MockHttpServletRequest();
        requestWithUa.addHeader("User-Agent", "CustomBrowser/1.0");
        proxyService.proxyRequest(baseUrl + "/ua-client", requestWithUa);
        assertEquals("CustomBrowser/1.0", capturedUa1.get());

        // Test without client User-Agent -> fallback
        MockHttpServletRequest requestWithoutUa = new MockHttpServletRequest();
        proxyService.proxyRequest(baseUrl + "/ua-default", requestWithoutUa);
        assertEquals("PreonsURL-Proxy/1.0", capturedUa2.get());
    }

    @Test
    void proxy_ssrfBlockedWhenLoopbackNotAllowed_returns400() {
        ProxyResourceValidator.allowLoopbackForTesting = false;

        MockHttpServletRequest request = new MockHttpServletRequest();
        ResponseEntity<?> response = proxyService.proxyRequest("http://127.0.0.1:8080/test", request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void proxy_connectionRefused_returns502BadGateway() {
        // Port with no server running
        MockHttpServletRequest request = new MockHttpServletRequest();
        ResponseEntity<?> response = proxyService.proxyRequest("http://127.0.0.1:65530/test", request);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }
}
