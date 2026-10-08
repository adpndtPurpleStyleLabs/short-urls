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
        String openedIp,
        String openedCountry,
        String openedCity,
        boolean otpRequested,
        Instant otpRequestedAt,
        boolean pageOpened,
        Instant pageOpenedAt,
        String status,
        Instant createdAt
) {
    public LinkRecipientDto(
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
        this(id, shortUrlId, email, trackingToken, emailSent, emailSentAt, emailOpened, emailOpenedAt,
                null, null, null, otpRequested, otpRequestedAt, pageOpened, pageOpenedAt, status, createdAt);
    }
}
