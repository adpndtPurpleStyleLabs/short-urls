package com.preonsurl.apis.analytics.controller;

import com.preonsurl.apis.analytics.dto.AnalyticsResponse;
import com.preonsurl.apis.analytics.dto.UrlAnalyticsResponse;
import com.preonsurl.apis.analytics.service.AnalyticsService;
import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.config.OpenApiConfig;
import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.exception.UrlNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Analytics", description = "Endpoints for daily aggregated click analytics")
@RestController
@RequestMapping({"/api/analytics", "/analytics"})
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Operation(
            summary = "Overall daily aggregated clicks",
            description = "Retrieves daily aggregated click counts across all URLs owned by the authenticated user over a time range. Defaults to the last 30 days if dates are omitted.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @GetMapping(value = {"", "/overall", "/clicks"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AnalyticsResponse>> getOverallClicks(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            AuthenticatedUser user = resolveUser(currentUser);
            if (user == null || user.userId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required: user ID not found"));
            }

            AnalyticsResponse response = analyticsService.getOverallClicks(user.userId(), startDate, endDate);
            log.info("Overall analytics retrieved for userId={}: totalClicks={}, range=[{} to {}]",
                    user.userId(), response.totalClicks(), response.startDate(), response.endDate());
            return ResponseEntity.ok(ApiResponse.success(response, "Overall click analytics retrieved successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid overall analytics request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("Access denied for overall analytics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to retrieve overall analytics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal Server Error"));
        }
    }

    @Operation(
            summary = "URL daily aggregated clicks",
            description = "Retrieves daily aggregated click counts for a specific URL owned by the authenticated user over a time range. Provide either 'url', 'shortCode', or 'newUrl' parameter.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @GetMapping(value = "/url", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<UrlAnalyticsResponse>> getUrlClicks(
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "shortCode", required = false) String shortCode,
            @RequestParam(value = "newUrl", required = false) String newUrl,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        String query = (url != null && !url.isBlank()) ? url : ((shortCode != null && !shortCode.isBlank()) ? shortCode : newUrl);
        return handleUrlClicks(query, startDate, endDate, currentUser);
    }

    @Operation(
            summary = "URL daily aggregated clicks by path variable",
            description = "Retrieves daily aggregated click counts for a specific URL or shortCode given in the path.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @GetMapping(value = "/url/{identifier}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<UrlAnalyticsResponse>> getUrlClicksByPath(
            @PathVariable("identifier") String identifier,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return handleUrlClicks(identifier, startDate, endDate, currentUser);
    }

    private ResponseEntity<ApiResponse<UrlAnalyticsResponse>> handleUrlClicks(
            String query,
            LocalDate startDate,
            LocalDate endDate,
            AuthenticatedUser currentUser) {
        try {
            AuthenticatedUser user = resolveUser(currentUser);
            if (user == null || user.userId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required: user ID not found"));
            }

            if (query == null || query.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("URL or shortCode parameter is required"));
            }

            UrlAnalyticsResponse response = analyticsService.getUrlClicks(user.userId(), query.trim(), startDate, endDate);
            log.info("URL analytics retrieved for userId={}, query='{}': totalClicks={}, range=[{} to {}]",
                    user.userId(), query, response.totalClicks(), response.startDate(), response.endDate());
            return ResponseEntity.ok(ApiResponse.success(response, "URL click analytics retrieved successfully"));
        } catch (UrlNotFoundException e) {
            log.warn("URL not found for analytics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("Access denied for URL analytics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid URL analytics request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to retrieve URL analytics for query='{}': {}", query, e.getMessage(), e);
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
