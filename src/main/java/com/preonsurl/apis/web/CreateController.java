package com.preonsurl.apis.web;

import com.preonsurl.apis.dto.ApiResponse;
import com.preonsurl.apis.dto.CreateShortUrlRequest;
import com.preonsurl.apis.dto.CreateShortUrlResponse;
import com.preonsurl.apis.service.ShortUrlService;
import com.preonsurl.apis.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Short URL Creation", description = "Endpoints for creating and retrieving shortened URLs")
@RestController
@RequestMapping("/link")
public class CreateController {

    private static final Logger log = LoggerFactory.getLogger(CreateController.class);

    private final ShortUrlService shortUrlService;

    public CreateController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @Operation(
            summary = "Create or retrieve short URL",
            description = "Creates a new shortened URL with optional directory grouping, expiration date, and usage limit ('once', 'unlimited', or a positive integer). Requires API key authentication.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME),
                    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
            }
    )
    @PostMapping(value = "/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<CreateShortUrlResponse>> createShortUrl(@RequestBody @Valid CreateShortUrlRequest request) {
        try {

            CreateShortUrlResponse response = shortUrlService.createOrGetShortUrl(request);
            log.info("Short URL processed: code='{}', dirType='{}', existing={}, url='{}', usageLimit={}", response.shortCode(), response.dirType(), response.existing(), response.originalUrl(), response.usageLimit());
            return ResponseEntity.ok(ApiResponse.success(response, "Short URL created successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid short URL create request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to process short URL creation for url: {}", request.url(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal Server Error"));
        }
    }
}
