package com.preonsurl.apis.uptime.dto;

public record DailyUptimeDto(
        int dayIndex,
        String date,
        String status, // "operational", "partial", "down"
        double uptimePercentage,
        String label
) {}
