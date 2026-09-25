package com.preonsurl.apis.publiclink.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.AccessPolicies;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Request payload for creating a public shortened link with psecure subdomain")
public record CreatePublicLinkRequest(
        @NotBlank(message = "URL cannot be blank")
        @Schema(description = "Destination URL", example = "https://www.perniaspopupshop.com/")
        String url,

        @Schema(description = "Link serving mode: REDIRECT, PROXY, or MIRROR", example = "REDIRECT", defaultValue = "REDIRECT")
        String linkMode,

        @JsonProperty("addShortCode")
        @Schema(description = "Flag indicating whether to generate a short code", defaultValue = "true")
        Boolean addShortCode,

        @Valid
        @Schema(description = "Access policies including PIN, password, or security mode")
        AccessPolicies accessPolicies
) {
    public String resolvedLinkMode() {
        return (linkMode != null && !linkMode.isBlank()) ? linkMode.trim().toUpperCase() : "REDIRECT";
    }

    public boolean resolvedAddShortCode() {
        return addShortCode == null || addShortCode;
    }
}
