package com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Country-based access restriction")
public record CountryPolicy(

        @NotEmpty(message = "countries cannot be empty")
        @Schema(
                description = "ISO 3166-1 alpha-2 country codes",
                example = "[\"IN\", \"US\", \"GB\"]"
        )
        List<String> countries

) {
}
