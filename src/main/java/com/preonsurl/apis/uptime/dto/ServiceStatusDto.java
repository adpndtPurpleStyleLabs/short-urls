package com.preonsurl.apis.uptime.dto;

public record ServiceStatusDto(
        String name,
        String description,
        String region,
        String status, // "Operational", "Degraded", "Outage"
        String statusDotClass // "operational", "partial", "down"
) {}
