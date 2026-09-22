package com.preonsurl.apis;

import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.link.service.MirrorCookieService;
import com.preonsurl.apis.link.service.MirrorCssRewriter;
import com.preonsurl.apis.link.service.MirrorHtmlRewriter;
import com.preonsurl.apis.link.service.MirrorService;
import com.preonsurl.apis.link.service.MirrorUrlResolver;
import com.preonsurl.apis.link.service.ProxyResourceValidator;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MirrorServiceTest {

    private MirrorService mirrorService;
    private HttpServer server;
    private int port;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        ProxyResourceValidator.allowLoopbackForTesting = true;

        MirrorUrlResolver urlResolver = new MirrorUrlResolver();
        MirrorCssRewriter cssRewriter = new MirrorCssRewriter(urlResolver);
        MirrorHtmlRewriter htmlRewriter = new MirrorHtmlRewriter(urlResolver, cssRewriter);
        MirrorCookieService cookieService = new MirrorCookieService();

        mirrorService = new MirrorService(urlResolver, htmlRewriter, cssRewriter, cookieService);

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

    private NewUrl createMockEntity(String originalUrl, String shortCode) {
        NewUrl entity = new NewUrl();
        entity.setId(100L);
        entity.setShortCode(shortCode);
        entity.setNewUrl("http://localhost:8081/" + shortCode);
        entity.setOriginalUrl(originalUrl);
        entity.setLinkMode(LinkMode.MIRROR);
        entity.setActive(true);
        return entity;
    }

    @Test
    void mirror_htmlPage_rewritesUrlsCorrectly() throws Exception {
        String upstreamHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <link rel="stylesheet" href="/pub/static/style.css">
                </head>
                <body>
                    <a href="/products/123">Product 123</a>
                    <img src="/pub/media/photo.jpg" alt="Photo">
                </body>
                </html>
                """;

        server.createContext("/sale", exchange -> {
            byte[] bytes = upstreamHtml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        NewUrl entity = createMockEntity(baseUrl + "/sale", "1BiVqa8OZJl");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1BiVqa8OZJl");
        request.addHeader("User-Agent", "Mozilla/5.0");

        ResponseEntity<?> response = mirrorService.mirrorRequest("1BiVqa8OZJl", "/", entity, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst("Content-Type").contains("text/html"));

        assertNotNull(response.getBody());
        String body = new String(((Resource) response.getBody()).getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(body.contains("href=\"/1BiVqa8OZJl/pub/static/style.css\""));
        assertTrue(body.contains("href=\"/1BiVqa8OZJl/products/123\""));
        assertTrue(body.contains("src=\"/1BiVqa8OZJl/pub/media/photo.jpg\""));
    }

    @Test
    void mirror_cssAsset_rewritesCssCorrectly() throws Exception {
        String upstreamCss = """
                @import url("/css/base.css");
                .hero {
                    background: url('/images/hero.png');
                }
                """;

        server.createContext("/pub/static/style.css", exchange -> {
            byte[] bytes = upstreamCss.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        NewUrl entity = createMockEntity(baseUrl + "/sale", "1BiVqa8OZJl");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1BiVqa8OZJl/pub/static/style.css");

        ResponseEntity<?> response = mirrorService.mirrorRequest("1BiVqa8OZJl", "/pub/static/style.css", entity, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst("Content-Type").contains("text/css"));

        String body = new String(((Resource) response.getBody()).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("url(\"/1BiVqa8OZJl/css/base.css\")"));
        assertTrue(body.contains("url('/1BiVqa8OZJl/images/hero.png')"));
    }

    @Test
    void mirror_binaryAsset_streamsDirectlyWithoutRewriting() throws Exception {
        byte[] dummyBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

        server.createContext("/pub/media/logo.png", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, dummyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(dummyBytes);
            }
        });

        NewUrl entity = createMockEntity(baseUrl + "/sale", "1BiVqa8OZJl");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1BiVqa8OZJl/pub/media/logo.png");

        ResponseEntity<?> response = mirrorService.mirrorRequest("1BiVqa8OZJl", "/pub/media/logo.png", entity, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/png", response.getHeaders().getFirst("Content-Type"));

        byte[] receivedBytes = ((Resource) response.getBody()).getInputStream().readAllBytes();
        assertArrayEquals(dummyBytes, receivedBytes);
    }

    @Test
    void mirror_redirect_rewritesSameOriginLocationHeader() {
        server.createContext("/sale", exchange -> {
            exchange.getResponseHeaders().set("Location", "/sale/women");
            exchange.sendResponseHeaders(302, -1);
        });

        NewUrl entity = createMockEntity(baseUrl + "/sale", "1BiVqa8OZJl");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1BiVqa8OZJl");

        ResponseEntity<?> response = mirrorService.mirrorRequest("1BiVqa8OZJl", "/", entity, request);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("/1BiVqa8OZJl/sale/women", response.getHeaders().getFirst("Location"));
    }

    @Test
    void mirror_thirdPartyRedirect_passesThroughLocation() {
        server.createContext("/sale", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://auth.external.com/login");
            exchange.sendResponseHeaders(302, -1);
        });

        NewUrl entity = createMockEntity(baseUrl + "/sale", "1BiVqa8OZJl");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1BiVqa8OZJl");

        ResponseEntity<?> response = mirrorService.mirrorRequest("1BiVqa8OZJl", "/", entity, request);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://auth.external.com/login", response.getHeaders().getFirst("Location"));
    }

    @Test
    void mirror_cookies_stripsDomain() {
        server.createContext("/sale", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.getResponseHeaders().add("Set-Cookie", "sid=12345; Domain=127.0.0.1; Path=/; HttpOnly");
            byte[] bytes = "<html><body>Home</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        NewUrl entity = createMockEntity(baseUrl + "/sale", "1BiVqa8OZJl");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1BiVqa8OZJl");

        ResponseEntity<?> response = mirrorService.mirrorRequest("1BiVqa8OZJl", "/", entity, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<String> cookies = response.getHeaders().get("Set-Cookie");
        assertNotNull(cookies);
        assertFalse(cookies.isEmpty());
        String cookie = cookies.get(0);
        assertTrue(cookie.contains("sid=12345"));
        assertFalse(cookie.toLowerCase().contains("domain="));
    }

    @Test
    void mirror_methodNotAllowed_onUnsupportedMethod() {
        NewUrl entity = createMockEntity(baseUrl + "/sale", "1BiVqa8OZJl");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/1BiVqa8OZJl");

        ResponseEntity<?> response = mirrorService.mirrorRequest("1BiVqa8OZJl", "/", entity, request);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
    }

    @Test
    void mirror_ssrfBlocked_returns400() {
        ProxyResourceValidator.allowLoopbackForTesting = false;

        NewUrl entity = createMockEntity("http://127.0.0.1:" + port + "/sale", "1BiVqa8OZJl");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/1BiVqa8OZJl");

        ResponseEntity<?> response = mirrorService.mirrorRequest("1BiVqa8OZJl", "/", entity, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
