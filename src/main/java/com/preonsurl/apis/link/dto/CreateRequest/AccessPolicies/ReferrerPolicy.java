package com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "HTTP referrer-based access restriction")
public record ReferrerPolicy(

        @NotEmpty(message = "referrers cannot be empty")
        @Schema(
                description = "Allowed referrer domains or patterns",
                example = "[\"example.com\", \"*.example.org\"]"
        )
        List<String> referrers

) {
}