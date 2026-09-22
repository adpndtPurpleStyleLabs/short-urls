package com.preonsurl.apis;

import com.preonsurl.apis.link.service.MirrorUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class MirrorUrlResolverTest {

    private MirrorUrlResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MirrorUrlResolver();
    }

    @Test
    void testResolveTargetUriRoot() {
        URI base = URI.create("https://www.ogaan.com/sale");
        URI resolvedEmpty = resolver.resolveTargetUri(base, "", null);
        assertEquals("https://www.ogaan.com/sale", resolvedEmpty.toString());

        URI resolvedSlash = resolver.resolveTargetUri(base, "/", null);
        assertEquals("https://www.ogaan.com/sale", resolvedSlash.toString());
    }

    @Test
    void testResolveTargetUriSubpath() {
        URI base = URI.create("https://www.ogaan.com/sale");
        URI resolved = resolver.resolveTargetUri(base, "/pub/media/a.jpg", null);
        assertEquals("https://www.ogaan.com/pub/media/a.jpg", resolved.toString());
    }

    @Test
    void testResolveTargetUriWithQueryString() {
        URI base = URI.create("https://www.ogaan.com/sale?category=women");
        URI resolved = resolver.resolveTargetUri(base, "/search", "q=dress&sort=asc");
        assertEquals("https://www.ogaan.com/search?q=dress&sort=asc", resolved.toString());

        URI resolvedBaseQuery = resolver.resolveTargetUri(base, "/", null);
        assertEquals("https://www.ogaan.com/sale?category=women", resolvedBaseQuery.toString());
    }

    @Test
    void testResolveTargetUriRejectsDirectoryTraversal() {
        URI base = URI.create("https://www.ogaan.com/sale");
        assertThrows(IllegalArgumentException.class, () ->
                resolver.resolveTargetUri(base, "/../admin/secret", null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                resolver.resolveTargetUri(base, "/pub/../../etc/passwd", null)
        );
    }

    @Test
    void testIsAllowedMirrorOrigin() {
        URI origin = URI.create("https://www.ogaan.com/sale");

        // Same scheme, same host, default port
        assertTrue(resolver.isAllowedMirrorOrigin(URI.create("https://www.ogaan.com/pub/media/image.png"), origin));
        assertTrue(resolver.isAllowedMirrorOrigin(URI.create("https://www.ogaan.com:443/about"), origin));

        // Different host
        assertFalse(resolver.isAllowedMirrorOrigin(URI.create("https://cdn.ogaan.com/pub/media/image.png"), origin));
        assertFalse(resolver.isAllowedMirrorOrigin(URI.create("https://example.com/item"), origin));

        // Different scheme
        assertFalse(resolver.isAllowedMirrorOrigin(URI.create("http://www.ogaan.com/sale"), origin));

        // Different port
        assertFalse(resolver.isAllowedMirrorOrigin(URI.create("https://www.ogaan.com:8443/sale"), origin));
    }

    @Test
    void testToMirrorUrl() {
        URI currentDoc = URI.create("https://www.ogaan.com/sale");
        String mirrorUrl = resolver.toMirrorUrl("1BiVqa8OZJl", currentDoc, "https://www.ogaan.com/pub/static/style.css?v=2#header");
        assertEquals("/1BiVqa8OZJl/pub/static/style.css?v=2#header", mirrorUrl);

        assertEquals("/1BiVqa8OZJl/", resolver.toMirrorUrl("1BiVqa8OZJl", currentDoc, "https://www.ogaan.com"));
    }
}
