package com.preonsurl.apis.link.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "Request body for checking CORS compatibility for Proxy and Mirror modes")
public record CorsCheckRequest(
        @Schema(description = "Destination original target URL", example = "https://example.com/api/data", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Destination URL is required")
        String url,

        @Schema(description = "Origin of the serving domain", example = "https://short.mybrand.com")
        String origin,

        @Schema(description = "HTTP method to check", example = "GET", defaultValue = "GET")
        String method,

        @Schema(description = "HTTP headers to check", example = "[\"Content-Type\", \"Authorization\"]")
        List<String> headers
) {
}
