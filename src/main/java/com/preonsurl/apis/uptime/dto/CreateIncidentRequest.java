package com.preonsurl.apis.uptime.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIncidentRequest(
        @NotBlank(message = "Title is required")
        String title,
        String description,
        String status, // RESOLVED, INVESTIGATING, IDENTIFIED, MONITORING
        String severity, // MINOR, MAJOR, CRITICAL, MAINTENANCE
        String impactedService
) {}
