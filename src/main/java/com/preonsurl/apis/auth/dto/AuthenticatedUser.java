package com.preonsurl.apis.auth.dto;

public record AuthenticatedUser(
        Long userId,
        Long tenantId,
        String username
) {
}