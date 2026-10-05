package com.preonsurl.apis.dataExtraction.controller;

import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.dataExtraction.dto.CreateExtractionRequest;
import com.preonsurl.apis.dataExtraction.dto.ExtractionJobResponse;
import com.preonsurl.apis.dataExtraction.service.DataExtractionService;
import com.preonsurl.apis.link.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

@Slf4j
@Tag(name = "Data Extraction", description = "Endpoints for generating and downloading link and link-analytics exports")
@RestController
@RequestMapping("/api/extract")
public class DataExtractionController {

    private final DataExtractionService extractionService;

    public DataExtractionController(DataExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @Operation(summary = "Initiate a data extraction job", description = "Queues a background job to extract links or analytics data in CSV or JSON")
    @PostMapping
    public ResponseEntity<ApiResponse<ExtractionJobResponse>> createExtractionJob(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateExtractionRequest request) {

        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        try {
            ExtractionJobResponse response = extractionService.createJob(currentUser.userId(), request);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(response, "Extraction job queued successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "List recent extraction jobs", description = "Returns paginated data extraction reports for the authenticated user")
    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<Page<ExtractionJobResponse>>> getRecentJobs(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        Page<ExtractionJobResponse> jobs = extractionService.listJobs(currentUser.userId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(jobs, "Extraction jobs retrieved successfully"));
    }

    @Operation(summary = "Get extraction job status", description = "Returns the status and metadata of a specific extraction job")
    @GetMapping("/jobs/{publicId}")
    public ResponseEntity<ApiResponse<ExtractionJobResponse>> getJob(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String publicId) {

        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        try {
            ExtractionJobResponse job = extractionService.getJob(publicId, currentUser.userId());
            return ResponseEntity.ok(ApiResponse.success(job, "Extraction job details retrieved"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Download extracted file", description = "Downloads the generated CSV or JSON export file")
    @GetMapping("/download/{publicId}")
    public ResponseEntity<Resource> downloadExportFile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String publicId) {

        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            DataExtractionService.DownloadResult result = extractionService.getDownloadFile(publicId, currentUser.userId());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                    .contentType(MediaType.parseMediaType(result.contentType()))
                    .contentLength(result.contentLength())
                    .body(result.resource());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
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
