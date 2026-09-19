package com.preonsurl.apis.auth.dto;

public record LoginRequest(
        String username,
        String password
) {
}
