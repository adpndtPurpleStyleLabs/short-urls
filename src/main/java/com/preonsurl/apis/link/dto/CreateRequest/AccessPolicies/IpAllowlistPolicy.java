package com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "IP address allowlist configuration")
public record IpAllowlistPolicy(

        @NotEmpty(message = "IP allowlist cannot be empty")
        @Schema(
                description = "IPv4, IPv6, or CIDR addresses allowed to access the link",
                example = "[\"103.21.45.10\", \"103.21.45.0/24\"]"
        )
        List<String> addresses

) {
}