package com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies;

import com.preonsurl.apis.link.enums.AccessPolicyMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

@Schema(description = "Controls who can access the shortened URL")
public record AccessPolicies(

        @Schema(
                description = "Access mode. PUBLIC allows everyone. SECURED enables one or more access restrictions.",
                example = "SECURED",
                defaultValue = "PUBLIC"
        )
        AccessPolicyMode mode,

        @Valid
        @Schema(
                description = "PIN protection configuration"
        )
        PinPolicy pin,

        @Valid
        @Schema(description = "Password protection configuration")
        PasswordPolicy password,

        @Valid
        @Schema(description = "IP address allowlist configuration")
        IpAllowlistPolicy ipAllowlist,

        @Valid
        @Schema(description = "Country restriction configuration")
        CountryPolicy country,

        @Valid
        @Schema(
                description = "Device restriction configuration"
        )
        DevicePolicy device,

        @Valid
        @Schema(
                description = "Referrer restriction configuration"
        )
        ReferrerPolicy referrer

) {

    public static AccessPolicies publicAccess() {
        return new AccessPolicies(
                AccessPolicyMode.PUBLIC,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public boolean isPublic() {
        return mode == null ||
                mode == AccessPolicyMode.PUBLIC;
    }

    public boolean isSecured() {
        return mode == AccessPolicyMode.SECURED;
    }

    public boolean hasPolicies() {
        return pin != null
                || password != null
                || ipAllowlist != null
                || country != null
                || device != null
                || referrer != null;
    }

    public AccessPolicies validate() {
        if (isSecured() && !hasPolicies()) {
            throw new IllegalArgumentException(
                    "Secured access policy requires at least one restriction configured (pin, password, ipAllowlist, country, device, or referrer)"
            );
        }
        return this;
    }
}