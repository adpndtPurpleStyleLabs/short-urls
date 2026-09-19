package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.dto.CreateNewUrlResponse;
import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.config.OpenApiConfig;
import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.dto.CreateNewUrlRequest;
import com.preonsurl.apis.link.exception.UrlNotFoundException;
import com.preonsurl.apis.link.service.NewUrlService;
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

@Tag(name = "New URL Creation", description = "Endpoints for creating and retrieving new URLs")
@RestController
@RequestMapping("/link")
public class LinkController {

    private static final Logger log = LoggerFactory.getLogger(LinkController.class);

    private final NewUrlService newUrlService;

    public LinkController(NewUrlService newUrlService) {
        this.newUrlService = newUrlService;
    }

    @Operation(
            summary = "Create or retrieve new URL",
            description = "Creates a new new URL with optional directory grouping, expiration date, usage limit, notes, and tags. Requires API key or Bearer token authentication.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @PostMapping(value = "/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<CreateNewUrlResponse>> createNewUrl(
            @RequestBody @Valid CreateNewUrlRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            AuthenticatedUser user = resolveUser(currentUser);
            Long userId = user != null ? user.userId() : null;

            CreateNewUrlResponse response = newUrlService.createNewUrl(request, userId);
            log.info("New-URL processed: code='{}', dirType='{}', existing={}, url='{}', usageLimit={}, note='{}', tags={}",
                    response.newUrl(), response.dirType(), response.existing(), response.originalUrl(), response.usageLimit(), response.notes(), response.tags());
            return ResponseEntity.ok(ApiResponse.success(response, "New URL created successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid URL create request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to process new URL creation for url: {}", request.url(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal Server Error"));
        }
    }

    @Operation(
            summary = "Fetch new URL details",
            description = "Retrieves new URL details, notes, and tags by destination URL or new URL. Requires API key or Bearer token authentication.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @GetMapping
    public ResponseEntity<ApiResponse<CreateNewUrlResponse>> getLinkInfo(
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

            CreateNewUrlResponse response = newUrlService.getLinkInfo(fullUrl.trim(), user.userId());
            log.info("New URL info retrieved: url='{}', original='{}', userId={}", response.newUrl(), response.originalUrl(), user.userId());
            return ResponseEntity.ok(ApiResponse.success(response, "Link details retrieved successfully"));
        } catch (UrlNotFoundException e) {
            log.warn("New URL not found for fullUrl '{}': {}", fullUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("Access denied for fullUrl '{}': {}", fullUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid get link info request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch link info for fullUrl: {}", fullUrl, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Internal Server Error"));
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
