package com.preonsurl.apis.auth.dto;

public record RegisterResponse(
        String username,
        String email,
        boolean verified,
        boolean requiresVerification
) {
    public RegisterResponse(String username) {
        this(username, null, false, true);
    }
}