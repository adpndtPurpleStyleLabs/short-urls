package com.preonsurl.apis.contactUs.dto;

public record ContactResponse(
        boolean success,
        String message,
        Long id
) {
}