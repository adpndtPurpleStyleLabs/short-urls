package com.preonsurl.apis.publiclink.controller;

import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.ui.LinkUiRenderer;
import com.preonsurl.apis.publiclink.dto.CachedPublicSecureUrlDto;
import com.preonsurl.apis.publiclink.service.PublicLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.util.Optional;

@Profile("psecureServe")
@Tag(name = "Psecure Serving", description = "Endpoints for serving and resolving psecure links with PIN protection")
@Controller
public class PsecureServingController {
    private static final Logger log = LoggerFactory.getLogger(PsecureServingController.class);
    private final PublicLinkService publicLinkService;
    private final LinkUiRenderer linkUiRenderer;

    public PsecureServingController(PublicLinkService publicLinkService, LinkUiRenderer linkUiRenderer) {
        this.publicLinkService = publicLinkService;
        this.linkUiRenderer = linkUiRenderer;
    }

    @Operation(summary = "Serve psecure link", description = "Resolves psecure link from LRU cache or DB, verifies security challenge if PIN protected, and redirects")
    @GetMapping(value = {"/{code}"})
    public ResponseEntity<?> servePsecure(@PathVariable("code") String code, HttpServletRequest request) {
        return resolveAndServe(code, "psecure/" + code, request);
    }

    @Operation(summary = "Verify PIN and serve psecure link", description = "Validates submitted PIN. Redirects on success, re-prompts challenge on error.")
    @PostMapping(value = {"/psecure/{code}/verify", "/psecure/{code}"})
    public ResponseEntity<?> verifyPinAndServe(@PathVariable("code") String code, @RequestParam(value = "pin", required = false) String pin, HttpServletRequest request) {
        return processPinVerification(code, "psecure/" + code, pin, request);
    }

    /**
     * Internal resolution and serve helper, usable by both direct routes and domain forwarder.
     */
    public ResponseEntity<?> resolveAndServe(String shortKey, String targetPath, HttpServletRequest request) {
        // 1. Fetch from LRU cache; if not present, fetch from DB using short key
        Optional<CachedPublicSecureUrlDto> opt = publicLinkService.resolve(shortKey);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error("Public secure link not found"));
        }

        CachedPublicSecureUrlDto cached = opt.get();
        if (!cached.isActive()) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error("This public link is no longer active"));
        }

        // 2. PIN verification challenge
        if (cached.hasPin()) {
            if (!isVerified(request, cached)) {
                log.info("Prompting PIN challenge for psecure link shortKey='{}' [IP={}]", shortKey, publicLinkService.extractClientIp(request));
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(linkUiRenderer.renderSecurityChallenge(targetPath, true, false, null));
            }
        }

        // 3. Increment usage and generate event to save access log for requested public-serve
        publicLinkService.recordServeEvent(cached, request, "REDIRECT");

        log.info("Redirecting psecure link shortKey='{}' -> '{}' [IP={}]",
                shortKey, cached.getOriginalUrl(), publicLinkService.extractClientIp(request));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(cached.getOriginalUrl()))
                .build();
    }

    /**
     * Internal PIN verification helper.
     */
    public ResponseEntity<?> processPinVerification(String shortKey, String targetPath, String submittedPin, HttpServletRequest request) {
        Optional<CachedPublicSecureUrlDto> opt = publicLinkService.resolve(shortKey);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error("Public secure link not found"));
        }

        CachedPublicSecureUrlDto cached = opt.get();
        if (!cached.isActive()) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error("This public link is no longer active"));
        }

        // Verify PIN
        String expectedPin = cached.getPin();
        boolean valid = expectedPin != null && expectedPin.equals(submittedPin != null ? submittedPin.trim() : "");

        if (!valid) {
            log.warn("Invalid PIN attempt for psecure link shortKey='{}' [IP={}]",
                    shortKey, publicLinkService.extractClientIp(request));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.TEXT_HTML)
                    .body(linkUiRenderer.renderSecurityChallenge(targetPath, true, false, "Invalid PIN entered. Please try again."));
        }

        // Verification successful: issue cookie and record event
        log.info("PIN verified for psecure link shortKey='{}' -> redirecting to '{}' [IP={}]",
                shortKey, cached.getOriginalUrl(), publicLinkService.extractClientIp(request));

        publicLinkService.recordServeEvent(cached, request, "VERIFIED");

        ResponseCookie cookie = ResponseCookie.from("PREONS_PSEC_" + cached.getShortKey(), "VERIFIED")
                .path("/")
                .maxAge(600)
                .httpOnly(true)
                .sameSite("Lax")
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(URI.create(cached.getOriginalUrl()))
                .build();
    }

    private boolean isVerified(HttpServletRequest request, CachedPublicSecureUrlDto cached) {
        if (request == null || request.getCookies() == null) {
            return false;
        }

        String psecCookieName = "PREONS_PSEC_" + cached.getShortKey();
        String secCookieName = cached.getId() != null ? "PREONS_SEC_" + cached.getId() : null;

        for (Cookie cookie : request.getCookies()) {
            if (psecCookieName.equals(cookie.getName()) && "VERIFIED".equals(cookie.getValue())) {
                return true;
            }
            if (secCookieName != null && secCookieName.equals(cookie.getName()) && "VERIFIED".equals(cookie.getValue())) {
                return true;
            }
        }

        return false;
    }
}
