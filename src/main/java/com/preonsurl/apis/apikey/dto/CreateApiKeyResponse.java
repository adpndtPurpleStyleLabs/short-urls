package com.preonsurl.apis.apikey.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response payload after successfully creating an API key")
public record CreateApiKeyResponse(
        @Schema(description = "API key ID", example = "1")
        Long id,

        @Schema(description = "API key name", example = "Production Backend Key")
        String name,

        @Schema(description = "Raw API key (only returned once upon creation; keep it secure)", example = "pk_9b4e138a0f8240ef87a55283f3e791b8")
        String apiKey,

        @Schema(description = "Whether the key is active", example = "true")
        boolean active,

        @Schema(description = "Timestamp when the key was created")
        Instant createdAt
) {
}
