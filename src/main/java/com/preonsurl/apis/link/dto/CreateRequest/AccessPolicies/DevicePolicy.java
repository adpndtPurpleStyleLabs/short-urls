package com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Device-based access restriction")
public record DevicePolicy(

        @NotEmpty(message = "devices cannot be empty")
        @Schema(
                description = "Allowed device types",
                example = "[\"MOBILE\", \"DESKTOP\"]"
        )
        List<String> devices

) {
}
