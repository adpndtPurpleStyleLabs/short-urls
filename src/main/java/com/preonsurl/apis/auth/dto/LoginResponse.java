package com.preonsurl.apis.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Long expiresIn
) {}