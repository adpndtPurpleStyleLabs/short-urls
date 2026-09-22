package com.preonsurl.apis.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
        @NotBlank(message = "Current identifier cannot be empty")
        String currentIdentifier,

        @NotBlank(message = "New email address cannot be empty")
        @Email(message = "Invalid email address format")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String newEmail
) {}
