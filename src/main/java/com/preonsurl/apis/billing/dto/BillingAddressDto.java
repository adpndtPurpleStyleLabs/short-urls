package com.preonsurl.apis.billing.dto;

public record BillingAddressDto(
        String fullName,
        String company,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        String taxId
) {
}
