package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.LinkRecipient;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.link.repository.AccessPolicyRepository;
import com.preonsurl.apis.link.repository.LinkRecipientRepository;
import com.preonsurl.emailer.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class LinkOtpService {

    private static final Logger log = LoggerFactory.getLogger(LinkOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LinkRecipientRepository linkRecipientRepository;
    private final AccessPolicyRepository accessPolicyRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public LinkOtpService(
            LinkRecipientRepository linkRecipientRepository,
            AccessPolicyRepository accessPolicyRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder) {
        this.linkRecipientRepository = linkRecipientRepository;
        this.accessPolicyRepository = accessPolicyRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    public record RequestOtpOutcome(boolean success, String message) {}
    public record VerifyOtpOutcome(boolean success, String message, String redirectUrl) {}

    @Transactional
    public RequestOtpOutcome requestOtp(NewUrl entity, String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return new RequestOtpOutcome(false, "Please provide a valid email address.");
        }

        String email = rawEmail.trim().toLowerCase();
        Long shortUrlId = entity.getId();

        Optional<AccessPolicy> policyOpt = accessPolicyRepository.findByShortUrlId(shortUrlId);
        if (policyOpt.isEmpty() || !policyOpt.get().isOtpProtected()) {
            return new RequestOtpOutcome(false, "Email OTP verification is not enabled for this link.");
        }

        AccessPolicy policy = policyOpt.get();
        Optional<LinkRecipient> recipientOpt = linkRecipientRepository.findByShortUrlIdAndEmailIgnoreCase(shortUrlId, email);

        LinkRecipient recipient;
        if (recipientOpt.isPresent()) {
            recipient = recipientOpt.get();
        } else {
            // Check if email is in policy.getOtpEmails()
            boolean isAllowed = false;
            if (policy.getOtpEmails() != null && !policy.getOtpEmails().isBlank()) {
                isAllowed = Arrays.stream(policy.getOtpEmails().split(","))
                        .map(String::trim)
                        .anyMatch(e -> e.equalsIgnoreCase(email));
            }
            if (!isAllowed) {
                log.warn("Access denied for email '{}' on shortUrlId={}: email not in authorized recipients list.", email, shortUrlId);
                return new RequestOtpOutcome(false, "This email address is not authorized to access this link.");
            }
            // Allowed via policy: create recipient record
            recipient = new LinkRecipient(shortUrlId, email, UUID.randomUUID().toString().replace("-", ""));
        }

        // Generate 6-digit numeric OTP code
        int codeInt = RANDOM.nextInt(900_000) + 100_000;
        String code = String.valueOf(codeInt);

        recipient.setOtpCodeHash(passwordEncoder.encode(code));
        recipient.setOtpExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        recipient.setOtpRequested(true);
        recipient.setOtpRequestedAt(Instant.now());
        linkRecipientRepository.save(recipient);

        log.info("OTP requested for recipient '{}' on shortUrlId={} (url='{}')", email, shortUrlId, entity.getNewUrl());

        try {
            emailService.sendLinkAccessOtp(email, code, entity.getNewUrl());
        } catch (Exception e) {
            log.warn("Failed to dispatch link access OTP to '{}': {}", email, e.getMessage());
        }

        return new RequestOtpOutcome(true, "A 6-digit verification code has been sent to " + email);
    }

    @Transactional
    public VerifyOtpOutcome verifyOtp(NewUrl entity, String rawEmail, String otp) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return new VerifyOtpOutcome(false, "Please provide the email address used to request the OTP.", null);
        }
        if (otp == null || otp.isBlank()) {
            return new VerifyOtpOutcome(false, "Please enter the 6-digit verification code.", null);
        }

        String email = rawEmail.trim().toLowerCase();
        Long shortUrlId = entity.getId();

        Optional<LinkRecipient> recipientOpt = linkRecipientRepository.findByShortUrlIdAndEmailIgnoreCase(shortUrlId, email);
        if (recipientOpt.isEmpty()) {
            return new VerifyOtpOutcome(false, "No active verification request found for this email.", null);
        }

        LinkRecipient recipient = recipientOpt.get();
        if (recipient.getOtpExpiresAt() == null || Instant.now().isAfter(recipient.getOtpExpiresAt())) {
            return new VerifyOtpOutcome(false, "Verification code has expired. Please request a new code.", null);
        }

        if (recipient.getOtpCodeHash() == null || !passwordEncoder.matches(otp.trim(), recipient.getOtpCodeHash())) {
            return new VerifyOtpOutcome(false, "Invalid verification code. Please check and try again.", null);
        }

        // OTP Validated!
        recipient.setPageOpened(true);
        recipient.setPageOpenedAt(Instant.now());
        recipient.setOtpCodeHash(null); // One-time use: clear active OTP hash
        linkRecipientRepository.save(recipient);

        LinkMode mode = entity.getLinkMode() != null ? entity.getLinkMode() : LinkMode.REDIRECT;
        String destinationUrl = (mode == LinkMode.PROXY || mode == LinkMode.MIRROR)
                ? (entity.getNewUrl() != null && !entity.getNewUrl().isBlank() ? entity.getNewUrl() : entity.getOriginalUrl())
                : entity.getOriginalUrl();

        log.info("OTP successfully verified for recipient '{}' on shortUrlId={} [mode={}] -> access granted to '{}'",
                email, shortUrlId, mode, destinationUrl);

        return new VerifyOtpOutcome(true, "Access granted.", destinationUrl);
    }
}
