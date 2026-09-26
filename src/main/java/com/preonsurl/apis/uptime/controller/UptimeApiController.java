package com.preonsurl.apis.uptime.controller;

import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.uptime.dto.CreateIncidentRequest;
import com.preonsurl.apis.uptime.dto.IncidentDto;
import com.preonsurl.apis.uptime.dto.UptimeDashboardDto;
import com.preonsurl.apis.uptime.service.UptimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/public/uptime", "/api/uptime"})
@Tag(name = "Uptime & System Status", description = "Public system status, SLA, incidents and deployment information")
public class UptimeApiController {

    private final UptimeService uptimeService;

    public UptimeApiController(UptimeService uptimeService) {
        this.uptimeService = uptimeService;
    }

    @Operation(summary = "Get System Uptime & Status", description = "Returns cached 90-day uptime, SLA metrics, services, latest deployment, and incidents with 1 hour TTL")
    @GetMapping
    public ResponseEntity<ApiResponse<UptimeDashboardDto>> getUptimeDashboard() {
        UptimeDashboardDto dashboard = uptimeService.getDashboardData();
        return ResponseEntity.ok(ApiResponse.success(dashboard, "Uptime status retrieved successfully"));
    }

    @Operation(summary = "Trigger Health Check", description = "Manually triggers health check against health endpoint and logs to uptime_table if status changes")
    @PostMapping("/check-health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerHealthCheck() {
        boolean changed = uptimeService.checkHealthAndRecordIfChanged();
        UptimeDashboardDto fresh = uptimeService.getDashboardData();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "statusChanged", changed,
                "overallStatus", fresh.overallStatus(),
                "responseLatency", fresh.metrics().get("responseLatency")
        ), changed ? "Health status changed and logged to uptime_table" : "Health status checked, no change"));
    }

    @Operation(summary = "Log Incident", description = "Logs a new incident to incident_table and invalidates the 1-hour cache")
    @PostMapping("/incidents")
    public ResponseEntity<ApiResponse<IncidentDto>> createIncident(@Valid @RequestBody CreateIncidentRequest req) {
        IncidentDto created = uptimeService.createIncident(req);
        return ResponseEntity.ok(ApiResponse.success(created, "Incident logged successfully"));
    }

    @Operation(summary = "Resolve Incident", description = "Marks an existing incident as resolved in incident_table and invalidates cache")
    @PostMapping("/incidents/{id}/resolve")
    public ResponseEntity<ApiResponse<IncidentDto>> resolveIncident(@PathVariable Long id) {
        IncidentDto resolved = uptimeService.resolveIncident(id);
        return ResponseEntity.ok(ApiResponse.success(resolved, "Incident marked as resolved"));
    }
}
