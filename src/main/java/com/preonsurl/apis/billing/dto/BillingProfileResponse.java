package com.preonsurl.apis.billing.dto;

import java.time.Instant;

public record BillingProfileResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        String plan,
        boolean isPro,
        Instant planUpdatedAt,
        BillingAddressDto address,
        String proPriceFormatted,
        String currency
) {
}
