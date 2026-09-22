package com.preonsurl.apis.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        boolean verified,
        String email
) {
    public LoginResponse(String accessToken, String tokenType, Long expiresIn) {
        this(accessToken, tokenType, expiresIn, true, null);
    }
}