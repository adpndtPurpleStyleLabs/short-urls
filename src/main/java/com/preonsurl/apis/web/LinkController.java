package com.preonsurl.apis.web;

import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.config.OpenApiConfig;
import com.preonsurl.apis.dto.ApiResponse;
import com.preonsurl.apis.dto.CreateShortUrlRequest;
import com.preonsurl.apis.dto.CreateShortUrlResponse;
import com.preonsurl.apis.exception.UrlNotFoundException;
import com.preonsurl.apis.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Short URL Creation", description = "Endpoints for creating and retrieving shortened URLs")
@RestController
@RequestMapping("/link")
public class LinkController {

    private static final Logger log = LoggerFactory.getLogger(LinkController.class);

    private final ShortUrlService shortUrlService;

    public LinkController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @Operation(
            summary = "Create or retrieve short URL",
            description = "Creates a new shortened URL with optional directory grouping, expiration date, usage limit, notes, and tags. Requires API key or Bearer token authentication.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @PostMapping(value = "/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<CreateShortUrlResponse>> createShortUrl(
            @RequestBody @Valid CreateShortUrlRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            AuthenticatedUser user = resolveUser(currentUser);
            Long userId = user != null ? user.userId() : null;

            CreateShortUrlResponse response = shortUrlService.createOrGetShortUrl(request, userId);
            log.info("Short URL processed: code='{}', dirType='{}', existing={}, url='{}', usageLimit={}, note='{}', tags={}",
                    response.shortCode(), response.dirType(), response.existing(), response.originalUrl(), response.usageLimit(), response.notes(), response.tags());
            return ResponseEntity.ok(ApiResponse.success(response, "Short URL created successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid short URL create request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to process short URL creation for url: {}", request.url(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal Server Error"));
        }
    }

    @Operation(
            summary = "Fetch short URL details",
            description = "Retrieves short URL details, notes, and tags by destination URL or short URL. Requires API key or Bearer token authentication.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @GetMapping(value = {""})
    public ResponseEntity<ApiResponse<CreateShortUrlResponse>> getLinkInfo(
            @RequestParam(value = "fullUrl", required = false) String fullUrl,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            AuthenticatedUser user = resolveUser(currentUser);
            if (user == null || user.userId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required: user ID not found"));
            }

            if (fullUrl == null || fullUrl.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("URL parameter 'fullUrl' cannot be empty"));
            }

            CreateShortUrlResponse response = shortUrlService.getLinkInfo(fullUrl.trim(), user.userId());
            log.info("Short URL info retrieved: code='{}', dirType='{}', url='{}', userId={}",
                    response.shortCode(), response.dirType(), response.originalUrl(), user.userId());
            return ResponseEntity.ok(ApiResponse.success(response, "Link details retrieved successfully"));
        } catch (UrlNotFoundException e) {
            log.warn("Short URL not found for fullUrl '{}': {}", fullUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("Access denied for fullUrl '{}': {}", fullUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid get link info request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch link info for fullUrl: {}", fullUrl, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal Server Error"));
        }
    }

    private AuthenticatedUser resolveUser(AuthenticatedUser user) {
        if (user != null) {
            return user;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser authUser) {
            return authUser;
        }
        return null;
    }
}
