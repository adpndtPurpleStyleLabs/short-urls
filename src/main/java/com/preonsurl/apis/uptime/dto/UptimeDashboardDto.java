package com.preonsurl.apis.uptime.dto;

import java.util.List;
import java.util.Map;

public record UptimeDashboardDto(
        String overallStatus, // "operational", "degraded", "outage"
        String overallTitle,
        String overallDescription,
        String currentSla,
        Map<String, String> metrics, // currentUptime, targetSla, responseLatency, incidentCount
        String rollingUptimeText,
        List<DailyUptimeDto> history90Days,
        List<ServiceStatusDto> services,
        DeploymentDto latestDeployment,
        List<IncidentDto> incidents,
        String lastUpdatedIso,
        long cachedTtlSeconds
) {}
