package com.preonsurl.apis.billing.dto;

import jakarta.validation.constraints.Size;

public record SaveBillingAddressRequest(
        @Size(max = 150) String fullName,
        @Size(max = 150) String company,
        @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 30) String postalCode,
        @Size(max = 100) String country,
        @Size(max = 50) String taxId
) {
}
