package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.entity.LinkRecipient;
import com.preonsurl.apis.link.repository.LinkRecipientRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

@Tag(name = "Tracking", description = "Email open tracking beacon endpoint")
@RestController
@RequestMapping({"/api/track", "/track"})
public class TrackingController {

    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);

    // 1x1 transparent PNG binary bytes
    private static final byte[] TRANSPARENT_1PX_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, (byte) 0xc4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0a, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9c, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, (byte) 0xb4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, (byte) 0xae,
            0x42, 0x60, (byte) 0x82
    };

    private final LinkRecipientRepository linkRecipientRepository;

    public TrackingController(LinkRecipientRepository linkRecipientRepository) {
        this.linkRecipientRepository = linkRecipientRepository;
    }

    @Operation(summary = "Track email open beacon", description = "Records email open event and returns 1px transparent PNG")
    @GetMapping(value = "/email-open/{token}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> trackEmailOpen(@PathVariable("token") String token) {
        try {
            if (token != null && !token.isBlank()) {
                Optional<LinkRecipient> recipientOpt = linkRecipientRepository.findByTrackingToken(token.trim());
                if (recipientOpt.isPresent()) {
                    LinkRecipient recipient = recipientOpt.get();
                    if (!recipient.isEmailOpened()) {
                        recipient.setEmailOpened(true);
                        recipient.setEmailOpenedAt(Instant.now());
                        linkRecipientRepository.save(recipient);
                        log.info("Email open tracked for recipient '{}' (shortUrlId={})",
                                recipient.getEmail(), recipient.getShortUrlId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to process email tracking token '{}': {}", token, e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setCacheControl("no-cache, no-store, must-revalidate, max-age=0");
        headers.setPragma("no-cache");
        headers.setExpires(0L);

        return ResponseEntity.ok()
                .headers(headers)
                .body(TRANSPARENT_1PX_PNG);
    }
}
