package com.preonsurl.apis.domain.dto;

import com.preonsurl.apis.domain.entity.DomainStatus;

public record DomainVerificationResponse(
        boolean verified,
        String domain,
        DomainStatus status,
        String message
) {}
