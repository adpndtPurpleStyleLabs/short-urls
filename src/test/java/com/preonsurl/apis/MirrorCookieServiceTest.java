package com.preonsurl.apis;

import com.preonsurl.apis.link.service.MirrorCookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class MirrorCookieServiceTest {

    private MirrorCookieService cookieService;

    @BeforeEach
    void setUp() {
        cookieService = new MirrorCookieService();
    }

    @Test
    void testTransformSetCookieStripsDomain() {
        String originalCookie = "SESSIONID=abc123xyz; Domain=ogaan.com; Path=/; HttpOnly; Secure; SameSite=Lax";
        String transformed = cookieService.transformSetCookie(originalCookie, "1BiVqa8OZJl");

        assertNotNull(transformed);
        assertFalse(transformed.toLowerCase().contains("domain="));
        assertTrue(transformed.contains("SESSIONID=abc123xyz"));
        assertTrue(transformed.contains("HttpOnly"));
        assertTrue(transformed.contains("Path=/"));
        assertTrue(transformed.contains("Secure"));
        assertTrue(transformed.contains("SameSite=Lax"));
    }

    @Test
    void testTransformSetCookieWithoutDomainPreserved() {
        String originalCookie = "pref=dark; Path=/; Max-Age=3600";
        String transformed = cookieService.transformSetCookie(originalCookie, "1BiVqa8OZJl");

        assertNotNull(transformed);
        assertTrue(transformed.contains("pref=dark"));
        assertTrue(transformed.contains("Path=/"));
        assertTrue(transformed.contains("Max-Age=3600"));
    }
}
