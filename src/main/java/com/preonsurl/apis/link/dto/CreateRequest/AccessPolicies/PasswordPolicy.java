package com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Password protection configuration")
public record PasswordPolicy(

        @Size(
                min = 6,
                max = 128,
                message = "Password must contain between 6 and 128 characters"
        )
        @Schema(
                description = "Password required to access the link",
                example = "MySecretPassword123"
        )
        String password

) {
}