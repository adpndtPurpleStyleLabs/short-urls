package com.preonsurl.apis.web;

import com.preonsurl.apis.dto.ApiResponse;
import com.preonsurl.apis.dto.CreateShortUrlRequest;
import com.preonsurl.apis.dto.CreateShortUrlResponse;
import com.preonsurl.apis.service.ShortUrlService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/link")
public class CreateController {

    private static final Logger log = LoggerFactory.getLogger(CreateController.class);

    private final ShortUrlService shortUrlService;

    public CreateController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @PostMapping(value = "/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<CreateShortUrlResponse>> createShortUrl(@RequestBody @Valid CreateShortUrlRequest request) {
        try {
            CreateShortUrlRequest normalizedRequest = new CreateShortUrlRequest(
                            request.url().trim(),
                            request.dirType(),
                            request.expire()
            );
            CreateShortUrlResponse response = shortUrlService.createOrGetShortUrl(normalizedRequest);
            log.info("Short URL processed: code='{}', dirType='{}', existing={}, url='{}'", response.shortCode(), response.dirType(),response.existing(), response.originalUrl());
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
