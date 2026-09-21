package com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "PIN protection configuration")
public record PinPolicy(

        @Pattern(
                regexp = "^\\d{4,12}$",
                message = "PIN must contain between 4 and 12 digits"
        )
        @Schema(
                description = "PIN required to access the link",
                example = "482931"
        )
        String pin

) {
}
