package com.preonsurl.apis.uptime.dto;

public record IncidentDto(
        Long id,
        String dateFormatted,
        String title,
        String description,
        String status, // "Resolved", "Investigating", "Monitoring", "Operational"
        String severity, // "Minor", "Major", "Critical"
        String impactedService
) {}
