package com.preonsurl.apis.apikey.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for creating an API key")
public record CreateApiKeyRequest(
        @Schema(description = "Friendly name or description for the API key", example = "Production Backend Key")
        String name
) {
}
