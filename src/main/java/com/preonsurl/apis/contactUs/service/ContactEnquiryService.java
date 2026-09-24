package com.preonsurl.apis.contactUs.service;

import com.preonsurl.apis.contactUs.dto.ContactRequest;
import com.preonsurl.apis.contactUs.dto.ContactResponse;
import com.preonsurl.apis.contactUs.entity.ContactEnquiry;
import com.preonsurl.apis.contactUs.repository.ContactEnquiryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactEnquiryService {
    private final ContactEnquiryRepository contactEnquiryRepository;

    @Transactional
    public ContactResponse create(ContactRequest request) {
        ContactEnquiry enquiry = new ContactEnquiry(request.fullName(), request.email(), request.company(), request.topic(), request.message());
        ContactEnquiry saved = contactEnquiryRepository.save(enquiry);
        log.info("Contact enquiry created: id={}, topic={}, emailDomain={}, companyPresent={}", saved.getId(), saved.getTopic(), extractEmailDomain(saved.getEmail()), saved.getCompany() != null);

        return new ContactResponse(true, "Your message has been sent. We will get back to you soon.", saved.getId());
    }

    private String extractEmailDomain(String email) {
        if (email == null) {
            return "unknown";
        }

        int at = email.lastIndexOf('@');

        if (at >= 0 && at < email.length() - 1) {
            return email.substring(at + 1);
        }

        return "unknown";
    }
}
