package com.preonsurl.apis.billing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PaymentFailureRequest(
        @JsonAlias({"order_id", "razorpayOrderId", "razorpay_order_id"})
        String orderId,

        @JsonAlias({"payment_id", "razorpayPaymentId", "razorpay_payment_id"})
        String paymentId,

        @JsonAlias({"error_code", "code"})
        String errorCode,

        @JsonAlias({"error_description", "description"})
        String errorDescription,

        @JsonAlias({"error_reason", "reason"})
        String errorReason
) {
}

