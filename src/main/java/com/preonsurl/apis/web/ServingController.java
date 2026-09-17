package com.preonsurl.apis.web;

import com.preonsurl.apis.dto.ApiResponse;
import com.preonsurl.apis.exception.UrlExpiredException;
import com.preonsurl.apis.service.ShortUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ServingController.class);

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
        return ResponseEntity.ok(Map.of("service", "your-shortner", "status", "UP"));
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    public ResponseEntity<?> serveRootShortCode(@PathVariable String shortCode, HttpServletRequest request) {
        if (RESERVED_WORDS.contains(shortCode.toLowerCase())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Short URL not found"));
        }

        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");
        try {
            Optional<String> originalUrl = shortUrlService.resolveAndRecordClick(null, shortCode, ipAddress, userAgent, referer);

            if (originalUrl.isPresent()) {
                String target = originalUrl.get();
                log.info("Redirecting root code='{}' -> '{}' [IP={}]", shortCode, target, ipAddress);

                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
            }

            log.warn("Short code not found: '{}' [IP={}]", shortCode, ipAddress);

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Short URL not found"));
        } catch (UrlExpiredException e) {
            log.warn("Short code expired: '{}' [IP={}]", shortCode, ipAddress);
            return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("Short URL has expired"));
        }
    }

    @GetMapping("/{dirType:[a-zA-Z0-9_-]+}/{shortCode:[a-zA-Z0-9]+}")
    public ResponseEntity<?> serveDirectoryShortCode(
            @PathVariable String dirType,
            @PathVariable String shortCode,
            HttpServletRequest request
    ) {
        if (RESERVED_WORDS.contains(dirType.toLowerCase())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Short URL not found"));
        }

        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        try {
            Optional<String> originalUrl = shortUrlService.resolveAndRecordClick(dirType, shortCode, ipAddress, userAgent, referer);

            if (originalUrl.isPresent()) {
                String target = originalUrl.get();
                log.info("Redirecting directory code='{}/{}' -> '{}' [IP={}]", dirType, shortCode, target, ipAddress);
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
            }
            log.warn("Directory short code not found: '{}/{}' [IP={}]", dirType, shortCode, ipAddress);

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Short URL not found"));
        } catch (UrlExpiredException e) {
            log.warn("Directory short code expired: '{}/{}' [IP={}]", dirType, shortCode, ipAddress);
            return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("Short URL has expired"));
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
