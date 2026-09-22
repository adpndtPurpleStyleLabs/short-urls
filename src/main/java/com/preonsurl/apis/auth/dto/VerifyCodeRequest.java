package com.preonsurl.apis.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyCodeRequest(
        String email,
        String username,
        @NotBlank(message = "Verification code cannot be empty")
        @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be exactly 6 digits")
        String code
) {
    public VerifyCodeRequest(String email, String code) {
        this(email, null, code);
    }
}
