package com.preonsurl.apis.contactUs.dto;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record ContactRequest(

        @NotBlank(message = "Full name cannot be empty")
        @Size(max = 100, message = "Full name cannot exceed 100 characters")
        String fullName,

        @NotBlank(message = "Email cannot be empty")
        @Email(message = "Please provide a valid email address")
        @Size(max = 254, message = "Email cannot exceed 254 characters")
        String email,

        @Size(max = 150, message = "Company cannot exceed 150 characters")
        String company,

        @NotBlank(message = "Topic cannot be empty")
        @Size(max = 30, message = "Topic cannot exceed 30 characters")
        String topic,

        @NotBlank(message = "Message cannot be empty")
        @Size(
                min = 10,
                max = 5000,
                message = "Message must be between 10 and 5000 characters"
        )
        String message

) {

    public ContactRequest {
        fullName = fullName == null
                ? null
                : fullName.trim();

        email = email == null
                ? null
                : email.trim().toLowerCase(Locale.ROOT);

        company = company == null || company.isBlank()
                ? null
                : company.trim();

        topic = topic == null
                ? null
                : topic.trim().toUpperCase(Locale.ROOT);

        message = message == null
                ? null
                : message.trim();
    }
}