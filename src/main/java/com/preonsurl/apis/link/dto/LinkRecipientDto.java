package com.preonsurl.apis.link.dto;

import java.time.Instant;

public record LinkRecipientDto(
        Long id,
        Long shortUrlId,
        String email,
        String trackingToken,
        boolean emailSent,
        Instant emailSentAt,
        boolean emailOpened,
        Instant emailOpenedAt,
        boolean otpRequested,
        Instant otpRequestedAt,
        boolean pageOpened,
        Instant pageOpenedAt,
        String status,
        Instant createdAt
) {
}
