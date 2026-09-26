package com.preonsurl.apis.uptime.dto;

public record DeploymentDto(
        String commitRef,
        String version,
        String environment,
        String status,
        String summary,
        String deployedAtFormatted,
        String deployedTimeIso
) {}
