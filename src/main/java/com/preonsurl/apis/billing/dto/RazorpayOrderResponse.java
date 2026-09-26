package com.preonsurl.apis.billing.dto;

public record RazorpayOrderResponse(
        String orderId,
        Long amount,
        String currency,
        String keyId,
        String userEmail,
        String userName,
        String plan,
        String formattedAmount
) {
}
