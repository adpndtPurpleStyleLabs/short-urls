package com.preonsurl.apis.audit.controller;

import com.preonsurl.apis.audit.dto.AuditLogResponse;
import com.preonsurl.apis.audit.service.AuditLogService;
import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.link.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Compliance Audit Logs", description = "Endpoints for viewing and exporting immutable compliance audit logs")
@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "List audit logs", description = "Retrieves a paginated list of read-only compliance audit logs with optional date and action filters")
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "startDateTime", required = false) String startDateTime,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "endDateTime", required = false) String endDateTime,
            @PageableDefault(page = 0, size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        String effectiveStart = (startDate != null && !startDate.isBlank()) ? startDate : startDateTime;
        String effectiveEnd = (endDate != null && !endDate.isBlank()) ? endDate : endDateTime;

        Page<AuditLogResponse> logs = auditLogService.getLogs(
                currentUser.userId(), action, resourceType, effectiveStart, effectiveEnd, pageable);

        return ResponseEntity.ok(ApiResponse.success(logs, "Audit logs retrieved successfully"));
    }

    @Operation(summary = "Export audit logs", description = "Exports compliance audit logs to CSV or JSON with optional date and action filters")
    @GetMapping("/export")
    public ResponseEntity<Resource> exportAuditLogs(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "startDateTime", required = false) String startDateTime,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "endDateTime", required = false) String endDateTime,
            @RequestParam(value = "format", defaultValue = "CSV") String format) {

        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String effectiveStart = (startDate != null && !startDate.isBlank()) ? startDate : startDateTime;
        String effectiveEnd = (endDate != null && !endDate.isBlank()) ? endDate : endDateTime;

        AuditLogService.DownloadResult result = auditLogService.exportLogs(
                currentUser.userId(), action, resourceType, effectiveStart, effectiveEnd, format);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.contentLength())
                .body(result.resource());
    }

    private AuthenticatedUser resolveUser(AuthenticatedUser user) {
        if (user != null) return user;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            return principal;
        }
        return null;
    }
}
