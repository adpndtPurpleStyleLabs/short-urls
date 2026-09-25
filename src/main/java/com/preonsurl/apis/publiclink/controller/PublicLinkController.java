package com.preonsurl.apis.publiclink.controller;

import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.publiclink.dto.CreatePublicLinkRequest;
import com.preonsurl.apis.publiclink.dto.PublicLinkResponse;
import com.preonsurl.apis.publiclink.service.PublicLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("app")
@Tag(name = "Public Link", description = "Endpoints for creating publicly accessible shortened URLs with psecure subdomain")
@RestController
@RequestMapping("/api/public-links")
public class PublicLinkController {

    private static final Logger log = LoggerFactory.getLogger(PublicLinkController.class);

    private final PublicLinkService publicLinkService;

    public PublicLinkController(PublicLinkService publicLinkService) {
        this.publicLinkService = publicLinkService;
    }

    @Operation(
            summary = "Create public link",
            description = "Creates a public shortened link with psecure subdomain. Enforces 1 request per 2 seconds rate limit per client IP."
    )
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<PublicLinkResponse>> createPublicLink(
            @RequestBody @Valid CreatePublicLinkRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpRequest) {

        String clientIp = publicLinkService.extractClientIp(httpRequest);

        // 1. Enforce 1 request per 2 seconds rate limit
        if (!publicLinkService.getRateLimiter().tryAcquire(clientIp)) {
            log.warn("Rate limit exceeded for public link creation from IP='{}'", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(publicLinkService.getRateLimiter().getRetryAfterSeconds()))
                    .body(ApiResponse.error("Rate limit exceeded. Try After sometime"));
        }

        try {
            long startNs = System.nanoTime();
            Long userId = currentUser != null ? currentUser.userId() : null;
            PublicLinkResponse response = publicLinkService.createPublicLink(request, userId, httpRequest);
            long processingNs = System.nanoTime() - startNs;
            response = response.withProcessingNs(processingNs);
            log.info("Public link created: psecureUrl='{}', [IP={}]", response.psecureUrl(), clientIp);
            return ResponseEntity.ok(ApiResponse.success(response, "Public link created successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid public link request from IP='{}': {}", clientIp, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create public link from IP='{}' for url='{}'", clientIp, request.url(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal Server Error"));
        }
    }
}
