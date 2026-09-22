package com.preonsurl.apis.domain.dto;

import com.preonsurl.apis.domain.entity.DomainStatus;

import java.time.Instant;

public record CustomDomainResponse(
        Long id,
        String domain,
        DomainStatus status,
        String cnameTarget,
        String verificationError,
        boolean isDefault,
        String description,
        Instant createdAt,
        Instant verifiedAt
) {}
