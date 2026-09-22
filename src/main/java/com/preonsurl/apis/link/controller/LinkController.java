package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.dto.CreateNewUrlResponse;
import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.config.OpenApiConfig;
import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.dto.CreateRequest.CreateNewUrlRequest;
import com.preonsurl.apis.link.dto.EditNewUrlRequest;
import com.preonsurl.apis.link.exception.UrlNotFoundException;
import com.preonsurl.apis.link.service.NewUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import com.preonsurl.apis.link.dto.UrlListItemResponse;
import com.preonsurl.apis.link.dto.LinkAccessLogResponse;


@Profile("app")
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
            log.info("New-URL processed: code='{}', linkMode='{}', existing={}, url='{}', usageLimit={}, note='{}', tags={}",
                    response.newUrl(), response.linkMode(), response.existing(), response.originalUrl(), response.usageLimit(), response.notes(), response.tags());
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
    @GetMapping(value = {""})
    public ResponseEntity<ApiResponse<CreateNewUrlResponse>> getLinkInfo(
            @RequestParam(value = "newUrl", required = false) String newUrl,
            @RequestParam(value = "fullUrl", required = false) String fullUrl,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            AuthenticatedUser user = resolveUser(currentUser);
            if (user == null || user.userId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required: user ID not found"));
            }

            String queryUrl = (newUrl != null && !newUrl.isBlank()) ? newUrl : fullUrl;
            if (queryUrl == null || queryUrl.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("URL parameter 'newUrl' cannot be empty"));
            }

            CreateNewUrlResponse response = newUrlService.getLinkInfo(queryUrl.trim(), user.userId());
            log.info("New URL info retrieved: url='{}', original='{}', userId={}", response.newUrl(), response.originalUrl(), user.userId());
            return ResponseEntity.ok(ApiResponse.success(response, "Link details retrieved successfully"));
        } catch (UrlNotFoundException e) {
            log.warn("New URL not found for queryUrl: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("Access denied for queryUrl: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid get link info request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch link info: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Internal Server Error"));
        }
    }

    @Operation(
            summary = "Get paginated list of URLs",
            description = "Retrieves a paginated list of short URLs for the authenticated user, ordered by latest by default. Returns short link, original link, isEnabled status, and expiration reason (TIME, USAGE) if expired.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @GetMapping(value = {"/list", "/urls"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<UrlListItemResponse>>> listUrls(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            AuthenticatedUser user = resolveUser(currentUser);
            if (user == null || user.userId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required: user ID not found"));
            }

            Page<UrlListItemResponse> response = newUrlService.listUrls(user.userId(), pageable);
            log.info("Listed URLs for userId={}: page={}, size={}, totalElements={}",
                    user.userId(), response.getNumber(), response.getSize(), response.getTotalElements());
            return ResponseEntity.ok(ApiResponse.success(response, "URLs retrieved successfully"));
        } catch (AccessDeniedException e) {
            log.warn("Access denied for list URLs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid list URLs request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list URLs: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal Server Error"));
        }
    }

    @Operation(
            summary = "Edit an existing new URL",
            description = "Updates parameters of an existing short URL (destination target URL, custom path, expiration timestamp, usage limit, notes, tags, link mode, active status). The short URL identifier itself is immutable. Requires authentication.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )

    @PostMapping(value = "/edit", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CreateNewUrlResponse>> editNewUrl(
            @RequestBody @Valid EditNewUrlRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            AuthenticatedUser user = resolveUser(currentUser);
            if (user == null || user.userId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required: user ID not found"));
            }

            CreateNewUrlResponse response = newUrlService.editNewUrl(request, user.userId());
            log.info("New URL edited: url='{}', original='{}', userId={}", response.newUrl(), response.originalUrl(), user.userId());
            return ResponseEntity.ok(ApiResponse.success(response, "Link updated successfully"));
        } catch (UrlNotFoundException e) {
            log.warn("New URL not found for edit: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("Access denied for edit: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid edit request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to edit link: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal Server Error"));
        }
    }

    @Operation(
            summary = "List link access logs",
            description = "Retrieves a paginated list of access logs for a specific short URL (or all URLs of the user if no URL parameter is provided), ordered by latest first by default. Requires authentication.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @GetMapping(value = {"/logs", "/access-log", "/access-logs"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<LinkAccessLogResponse>>> listAccessLogs(
            @RequestParam(value = "newUrl", required = false) String newUrl,
            @RequestParam(value = "fullUrl", required = false) String fullUrl,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "shortCode", required = false) String shortCode,
            @PageableDefault(page = 0, size = 20, sort = "accessedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        String queryUrl = (url != null && !url.isBlank()) ? url
                : ((newUrl != null && !newUrl.isBlank()) ? newUrl
                : ((shortCode != null && !shortCode.isBlank()) ? shortCode : fullUrl));
        return handleListAccessLogs(queryUrl, pageable, currentUser);
    }

    @Operation(
            summary = "List link access logs by short code",
            description = "Retrieves a paginated list of access logs for a specific short URL given in the path, ordered by latest first by default. Requires authentication.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @GetMapping(value = {"/logs/{shortCode}", "/access-log/{shortCode}"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<LinkAccessLogResponse>>> listAccessLogsByPath(
            @PathVariable("shortCode") String shortCode,
            @PageableDefault(page = 0, size = 20, sort = "accessedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return handleListAccessLogs(shortCode, pageable, currentUser);
    }

    private ResponseEntity<ApiResponse<Page<LinkAccessLogResponse>>> handleListAccessLogs(
            String queryUrl,
            Pageable pageable,
            AuthenticatedUser currentUser) {
        try {
            AuthenticatedUser user = resolveUser(currentUser);
            if (user == null || user.userId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required: user ID not found"));
            }

            Page<LinkAccessLogResponse> response = newUrlService.listAccessLogs(user.userId(), queryUrl, pageable);
            log.info("Listed access logs for userId={}, queryUrl='{}': page={}, size={}, totalElements={}",
                    user.userId(), queryUrl, response.getNumber(), response.getSize(), response.getTotalElements());
            return ResponseEntity.ok(ApiResponse.success(response, "Access logs retrieved successfully"));
        } catch (UrlNotFoundException e) {
            log.warn("New URL not found for access logs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("Access denied for access logs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid list access logs request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list access logs: {}", e.getMessage(), e);
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
