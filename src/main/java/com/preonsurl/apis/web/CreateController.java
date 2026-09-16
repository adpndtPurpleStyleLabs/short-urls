package com.preonsurl.apis.web;

import com.preonsurl.apis.dto.CreateShortUrlRequest;
import com.preonsurl.apis.dto.CreateShortUrlResponse;
import com.preonsurl.apis.service.ShortUrlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class CreateController {

    private static final Logger log = LoggerFactory.getLogger(CreateController.class);

    private final ShortUrlService shortUrlService;

    public CreateController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @PostMapping(
            value = "/create",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> createShortUrl(
            @RequestBody(required = false) CreateShortUrlRequest jsonRequest,
            @RequestParam(value = "url", required = false) String formUrl,
            @RequestParam(value = "dirType", required = false) String formDirType
    ) {
        String url = (jsonRequest != null && jsonRequest.getUrl() != null) ? jsonRequest.getUrl() : formUrl;
        String dirType = (jsonRequest != null && jsonRequest.getDirType() != null) ? jsonRequest.getDirType() : formDirType;

        if (url == null || url.isBlank()) {
            log.warn("Create short URL request missing 'url' parameter");
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Parameter 'url' is required"
            ));
        }

        try {
            CreateShortUrlRequest request = new CreateShortUrlRequest(url.trim(), dirType);
            CreateShortUrlResponse response = shortUrlService.createOrGetShortUrl(request);
            log.info("Short URL processed: code='{}', dirType='{}', existing={}, url='{}'",
                    response.getShortCode(), response.getDirType(), response.isExisting(), response.getOriginalUrl());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid short URL create request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to process short URL creation for url: {}", url, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", e.getMessage()
            ));
        }
    }
}
