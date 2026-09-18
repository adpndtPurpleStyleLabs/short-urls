package com.preonsurl.apis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard generic API response wrapper")
public record ApiResponse<T>(
        @Schema(description = "Indicates whether the request was successful", example = "true")
        boolean success,

        @Schema(description = "Human-readable status or error message", example = "Short URL created successfully")
        String message,

        @Schema(description = "Response payload data")
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                "Success",
                data
        );
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(
                true,
                message,
                data
        );
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(
                false,
                message,
                null
        );
    }
}
