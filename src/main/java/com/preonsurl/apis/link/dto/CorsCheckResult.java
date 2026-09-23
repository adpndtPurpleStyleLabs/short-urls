package com.preonsurl.apis.link.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of CORS preflight and header evaluation")
public record CorsCheckResult(
        @Schema(description = "HTTP response status code from destination server", example = "200")
        int status,

        @Schema(description = "Whether the requested origin is allowed", example = "true")
        boolean originAllowed,

        @Schema(description = "Whether the requested method is allowed", example = "true")
        boolean methodAllowed,

        @Schema(description = "Whether the requested headers are allowed", example = "true")
        boolean headersAllowed,

        @Schema(description = "Access-Control-Allow-Origin header value returned by target", example = "*")
        String allowOrigin,

        @Schema(description = "Access-Control-Allow-Methods header value returned by target", example = "GET, POST, OPTIONS")
        String allowMethods,

        @Schema(description = "Access-Control-Allow-Headers header value returned by target", example = "Content-Type")
        String allowHeaders,

        @Schema(description = "Optional message or error description if preflight check could not complete", example = "Target server responded with 200 OK")
        String message
) {

    public CorsCheckResult(
            int status,
            boolean originAllowed,
            boolean methodAllowed,
            boolean headersAllowed,
            String allowOrigin,
            String allowMethods,
            String allowHeaders
    ) {
        this(status, originAllowed, methodAllowed, headersAllowed, allowOrigin, allowMethods, allowHeaders, null);
    }

    @JsonProperty("allowed")
    @Schema(description = "Whether all CORS conditions (origin, method, headers, status) passed", example = "true")
    public boolean allowed() {
        return originAllowed
                && methodAllowed
                && headersAllowed
                && status >= 200
                && status < 300;
    }
}
