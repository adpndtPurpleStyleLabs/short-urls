package com.preonsurl.apis.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddDomainRequest(
        @NotBlank(message = "Domain name cannot be blank")
        @Size(max = 255, message = "Domain name must not exceed 255 characters")
        String domain
) {}
