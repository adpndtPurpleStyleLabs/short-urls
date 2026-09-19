package com.preonsurl.apis.web;

import com.preonsurl.apis.dto.ApiResponse;
import com.preonsurl.apis.exception.UrlExpiredException;
import com.preonsurl.apis.exception.UrlUsageLimitExceededException;
import com.preonsurl.apis.service.ShortUrlServingCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Tag(name = "Short URL Redirection", description = "Endpoints for redirecting shortened URLs to destination target URLs")
@Controller
public class ServingController {

    private static final Logger log = LoggerFactory.getLogger(ServingController.class);

    private final ShortUrlServingCacheService servingCacheService;

    private static final Set<String> RESERVED_WORDS = Set.of(
            "create", "error", "favicon.ico", "api", "actuator", "health",
            "swagger-ui", "swagger-ui.html", "v3", "swagger-resources", "webjars", "api-docs", "docs"
    );

    public ServingController(ShortUrlServingCacheService servingCacheService) {
        this.servingCacheService = servingCacheService;
    }

    @Operation(summary = "Service Health Check", description = "Returns service health status")
    @GetMapping("/")
    @ResponseBody
    public ResponseEntity<?> root() {
        return ResponseEntity.ok(Map.of("service", "your-shortner", "status", "UP"));
    }

    @Operation(
            summary = "Redirect root short URL",
            description = "Resolves the given short code using LRU cache and redirects with HTTP 302 Found to destination URL. Returns 410 Gone if expired or usage limit exceeded."
    )
    @GetMapping("/{shortCode:[a-zA-Z0-9_-]+}")
    public ResponseEntity<?> serveRootShortCode(@PathVariable String shortCode, HttpServletRequest request) {
        if (RESERVED_WORDS.contains(shortCode.toLowerCase())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Short URL not found"));
        }

        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");
        try {
            Optional<String> originalUrl = servingCacheService.resolveAndServe(null, shortCode, ipAddress, userAgent, referer);

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
        } catch (UrlUsageLimitExceededException e) {
            log.warn("Short code usage limit exceeded: '{}' [IP={}]", shortCode, ipAddress);
            return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("Short URL usage limit reached"));
        }
    }

    @Operation(
            summary = "Redirect directory short URL",
            description = "Resolves directory short code (dirType/shortCode) using LRU cache and redirects with HTTP 302 Found to destination URL. Returns 410 Gone if expired or usage limit exceeded."
    )
    @GetMapping("/{dirType:[a-zA-Z0-9_-]+}/{shortCode:[a-zA-Z0-9_-]+}")
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
            Optional<String> originalUrl = servingCacheService.resolveAndServe(dirType, shortCode, ipAddress, userAgent, referer);

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
        } catch (UrlUsageLimitExceededException e) {
            log.warn("Directory short code usage limit exceeded: '{}/{}' [IP={}]", dirType, shortCode, ipAddress);
            return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("Short URL usage limit reached"));
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
