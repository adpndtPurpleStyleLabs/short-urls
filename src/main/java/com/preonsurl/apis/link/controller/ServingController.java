package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.exception.UrlExpiredException;
import com.preonsurl.apis.link.exception.UrlUsageLimitExceededException;
import com.preonsurl.apis.link.service.NewUrlServingCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Tag(name = "New URL Redirection", description = "Endpoints for redirecting New URLs to destination target URLs")
@Controller
public class ServingController {

    private static final Logger log = LoggerFactory.getLogger(ServingController.class);

    private final NewUrlServingCacheService servingCacheService;

    public ServingController(NewUrlServingCacheService servingCacheService) {
        this.servingCacheService = servingCacheService;
    }

    @Operation(summary = "Service Health Check", description = "Returns service health status")
    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<?> root() {
        return ResponseEntity.ok(Map.of("service", "007", "status", "UP"));
    }

    @Operation(
            summary = "Redirect root New URL",
            description = "Resolves the given new code using LRU cache and redirects with HTTP 302 Found to destination URL. Returns 410 Gone if expired or usage limit exceeded."
    )

    @GetMapping("/**")
    public ResponseEntity<?> serve(HttpServletRequest request) {
        String fullUrl = request.getRequestURL().toString();
        String path = "";
        if (request.getRequestURI().startsWith("/")) {
            path = request.getRequestURI().substring(1);
        }
        if (path.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
        }

        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");
        try {
            Optional<String> originalUrl = servingCacheService.resolveAndServe(fullUrl, ipAddress, userAgent, referer);

            if (originalUrl.isPresent()) {
                String target = originalUrl.get();
                log.info("Redirecting root fullUrl='{}' -> '{}' [IP={}]", fullUrl, target, ipAddress);

                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
            }

            log.warn("Full url not found: '{}' [IP={}]", path, ipAddress);

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("New URL not found"));
        } catch (UrlExpiredException e) {
            log.warn("New code expired: '{}' [IP={}]", path, ipAddress);
            return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL has expired"));
        } catch (UrlUsageLimitExceededException e) {
            log.warn("New code usage limit exceeded: '{}' [IP={}]", path, ipAddress);
            return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error("New URL usage limit reached"));
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
