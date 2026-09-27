package com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Email OTP protection policy configuration")
public record OtpPolicy(
        @Schema(description = "Whether OTP email protection is enabled", example = "true")
        Boolean enabled,

        @Schema(description = "Authorized recipient emails for OTP verification", example = "[\"user@example.com\"]")
        List<String> emails,

        @Schema(description = "Whether to dispatch individual invitation emails with open-tracking pixels upon link creation", example = "true")
        Boolean sendIndividualEmail
) {
    public boolean isEnabled() {
        return (enabled != null && enabled) || (emails != null && !emails.isEmpty());
    }

    public boolean shouldSendEmails() {
        return Boolean.TRUE.equals(sendIndividualEmail);
    }
}
