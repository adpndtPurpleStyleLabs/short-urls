package com.preonsurl.apis.contactUs.controller;

import com.preonsurl.apis.contactUs.dto.ContactRequest;
import com.preonsurl.apis.contactUs.dto.ContactResponse;
import com.preonsurl.apis.contactUs.service.ContactEnquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {

    private final ContactEnquiryService contactEnquiryService;

    @PostMapping
    public ResponseEntity<ContactResponse> submitContact(@Valid @RequestBody ContactRequest request) {
        return ResponseEntity.ok(contactEnquiryService.create(request));
    }
}