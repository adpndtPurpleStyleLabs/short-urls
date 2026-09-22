package com.preonsurl.apis.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendCodeRequest(
        @NotBlank(message = "Email address cannot be empty")
        String email,
        String username
) {
    public ResendCodeRequest(String email) {
        this(email, null);
    }
}
