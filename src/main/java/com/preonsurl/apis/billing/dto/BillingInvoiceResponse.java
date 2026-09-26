package com.preonsurl.apis.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BillingInvoiceResponse(
        Long id,
        String invoiceNumber,
        BigDecimal amount,
        String currency,
        String formattedAmount,
        String plan,
        String status,
        String razorpayPaymentId,
        Instant paidAt,
        Instant createdAt,
        String customerName,
        String customerCompany,
        String downloadUrl
) {
}
