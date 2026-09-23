package com.preonsurl.apis.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name cannot be empty")
        @Size(max = 100, message = "Full name cannot exceed 100 characters")
        String fullName,

        @NotBlank(message = "Username cannot be empty")
        @Size(min = 3, max = 20, message = "username must be between 3 and 20 characters")
        String username,

        @NotBlank(message = "Email cannot be empty")
        @Email(message = "Please provide a valid email address")
        @Size(
                max = 254,
                message = "Email cannot exceed 254 characters"
        )
        String email,

        @NotBlank(message = "Password cannot be empty")
        @Size(min = 8, max = 20, message = "password must be between 8 and 20 characters")
        String password
) {
    public RegisterRequest(String fullName, String username, String password) {
        this(fullName, username, null, password);
    }
}