package com.preonsurl.apis.domain.dto;

public record DomainAvailabilityResponse(
        String domain,
        boolean available,
        String message
) {}
