package com.preonsurl.apis.link.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload to share a shortened link via a tracked email")
public record ShareLinkEmailRequest(
        @Schema(description = "Recipient email address(es), comma or space separated", example = "colleague@example.com")
        @NotBlank(message = "Recipient email is required")
        String recipientEmail,

        @Schema(description = "Optional CC email address(es), comma or space separated", example = "manager@example.com")
        String ccEmail,

        @Schema(description = "Optional email subject line", example = "Alex is sharing a secured url")
        String subject,

        @Schema(description = "Optional custom message to include in the email body", example = "Please review this confidential invoice link.")
        String message,

        @Schema(description = "Optional sender display name", example = "Alex")
        String senderName,

        @Schema(description = "Send individual emails to each recipient (true) or a single email to all recipients at once (false)", example = "true")
        Boolean sendIndividually
) {
    public ShareLinkEmailRequest(String recipientEmail, String subject, String message, String senderName) {
        this(recipientEmail, null, subject, message, senderName, true);
    }

    public ShareLinkEmailRequest(String recipientEmail, String ccEmail, String subject, String message, String senderName) {
        this(recipientEmail, ccEmail, subject, message, senderName, true);
    }
}

