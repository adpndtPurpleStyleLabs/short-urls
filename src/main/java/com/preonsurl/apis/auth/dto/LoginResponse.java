package com.preonsurl.apis.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        boolean verified,
        String email,
        String plan,
        String fullName
) {
    public LoginResponse(String accessToken, String tokenType, Long expiresIn) {
        this(accessToken, tokenType, expiresIn, true, null, "FREE", null);
    }

    public LoginResponse(String accessToken, String tokenType, Long expiresIn, boolean verified, String email) {
        this(accessToken, tokenType, expiresIn, verified, email, "FREE", null);
    }
}