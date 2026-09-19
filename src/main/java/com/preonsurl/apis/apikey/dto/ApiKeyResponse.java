package com.preonsurl.apis.apikey.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "API key metadata and status")
public record ApiKeyResponse(
        @Schema(description = "API key ID", example = "1")
        Long id,

        @Schema(description = "API key name", example = "Production Backend Key")
        String name,

        @Schema(description = "Whether the key is active", example = "true")
        boolean active,

        @Schema(description = "Timestamp when the key was created")
        Instant createdAt,

        @Schema(description = "Timestamp when the key was last used")
        Instant lastUsedAt
) {
}
