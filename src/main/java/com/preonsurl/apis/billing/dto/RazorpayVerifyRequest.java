package com.preonsurl.apis.billing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record RazorpayVerifyRequest(
        @NotBlank
        @JsonAlias({"razorpayOrderId", "razorpay_order_id"})
        String orderId,

        @NotBlank
        @JsonAlias({"razorpayPaymentId", "razorpay_payment_id"})
        String paymentId,

        @JsonAlias({"razorpaySignature", "razorpay_signature"})
        String signature
) {
}

