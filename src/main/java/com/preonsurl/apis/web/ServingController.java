package com.preonsurl.apis.web;

import com.preonsurl.apis.service.ShortUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Controller
public class ServingController {

    private final ShortUrlService shortUrlService;

    private static final Set<String> RESERVED_WORDS = Set.of(
            "create", "error", "favicon.ico", "api", "actuator", "health"
    );

    public ServingController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @GetMapping("/")
    @ResponseBody
    public ResponseEntity<?> root() {
        return ResponseEntity.ok(Map.of(
                "service", "preonsurl",
                "status", "UP"
        ));
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    public ResponseEntity<?> serveRootShortCode(
            @PathVariable("shortCode") String shortCode,
            HttpServletRequest request
    ) {
        if (RESERVED_WORDS.contains(shortCode.toLowerCase())) {
            return ResponseEntity.notFound().build();
        }

        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        Optional<String> originalUrl = shortUrlService.resolveAndRecordClick(null, shortCode, ipAddress, userAgent, referer);
        return originalUrl
                .map(url -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Short URL not found"));
    }

    @GetMapping("/{dirType:[a-zA-Z0-9_-]+}/{shortCode:[a-zA-Z0-9]+}")
    public ResponseEntity<?> serveDirectoryShortCode(
            @PathVariable("dirType") String dirType,
            @PathVariable("shortCode") String shortCode,
            HttpServletRequest request
    ) {
        if (RESERVED_WORDS.contains(dirType.toLowerCase())) {
            return ResponseEntity.notFound().build();
        }

        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        Optional<String> originalUrl = shortUrlService.resolveAndRecordClick(dirType, shortCode, ipAddress, userAgent, referer);
        return originalUrl
                .map(url -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Short URL not found"));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
