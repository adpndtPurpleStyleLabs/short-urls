package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.exception.ResourceNotFoundException;
import com.preonsurl.apis.link.service.NewUrlService;
import com.preonsurl.apis.link.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile("app")
@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class QrCodeController {
    private final QrCodeService qrCodeService;
    private final NewUrlService newUrlService;

    @GetMapping(value = "/{publicId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateQr(@PathVariable String publicId) {
       NewUrl url = newUrlService.getNewUrlByPublicId(publicId);
        byte[] qrCode = qrCodeService.generateQrCode(url.getNewUrl(), 800, 800);
        return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrCode);
    }
}