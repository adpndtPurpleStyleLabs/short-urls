package com.preonsurl.apis.publiclink.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing public shortened link with psecure subdomain")
public record PublicLinkResponse(
        @Schema(description = "Generated short code / key", example = "k8Js7Q")
        String psecureUrl,

        @Schema(description = "Link mode: REDIRECT, PROXY, or MIRROR", example = "REDIRECT")
        String linkMode,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Processsing time in nanoSec")
        long processingNs
) {
    public static PublicLinkResponse of(String psecureUrl, String linkMode, Instant createdAt, long processingNs) {
        return new PublicLinkResponse(psecureUrl, linkMode, createdAt, processingNs);
    }

    public PublicLinkResponse withProcessingNs(long processingNs) {
        return new PublicLinkResponse(
                psecureUrl,
                linkMode,
                createdAt,
                processingNs
        );
    }
}
