package com.preonsurl.apis.domain.service;

import com.preonsurl.apis.domain.dto.CustomDomainResponse;
import com.preonsurl.apis.domain.dto.DnsInstructionDto;
import com.preonsurl.apis.domain.dto.DomainVerificationResponse;
import com.preonsurl.apis.domain.entity.CustomDomain;
import com.preonsurl.apis.domain.entity.DomainStatus;
import com.preonsurl.apis.domain.repository.CustomDomainRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.preonsurl.apis.domain.dto.DomainAvailabilityResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CustomDomainService {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))*\\.[a-z]{2,63}$"
    );

    private final CustomDomainRepository customDomainRepository;
    private final DnsVerificationService dnsVerificationService;
    private final String defaultServeDomain;

    public CustomDomainService(
            CustomDomainRepository customDomainRepository,
            DnsVerificationService dnsVerificationService,
            @Value("${preonsurl.serve.domain:go.domain.com}") String defaultServeDomain) {
        this.customDomainRepository = customDomainRepository;
        this.dnsVerificationService = dnsVerificationService;
        this.defaultServeDomain = defaultServeDomain.trim().toLowerCase();
    }

    public String getDefaultServeDomain() {
        return defaultServeDomain;
    }

    @Transactional
    public CustomDomainResponse addDomain(Long userId, String rawDomain) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        String normalizedDomain = normalizeDomain(rawDomain);

        if (!isValidDomain(normalizedDomain)) {
            throw new IllegalArgumentException("Invalid domain format: " + rawDomain + ". Please enter a valid domain or subdomain (e.g. links.example.com).");
        }

        if (normalizedDomain.equalsIgnoreCase(defaultServeDomain)) {
            throw new IllegalArgumentException("Cannot register the system's default domain '" + defaultServeDomain + "'.");
        }

        if (customDomainRepository.existsByDomain(normalizedDomain)) {
            throw new IllegalArgumentException("Domain '" + normalizedDomain + "' is already registered by an account.");
        }

        CustomDomain domain = new CustomDomain(userId, normalizedDomain, defaultServeDomain);
        domain.setStatus(DomainStatus.VERIFICATION_REQUIRED);

        // Attempt initial CNAME resolution
        String resolvedCname = dnsVerificationService.resolveCname(normalizedDomain);
        if (matchesCname(resolvedCname, defaultServeDomain)) {
            domain.setStatus(DomainStatus.ACTIVE);
            domain.setVerifiedAt(Instant.now());
            domain.setVerificationError(null);
            log.info("Domain '{}' immediately verified during registration for user {}", normalizedDomain, userId);
        } else {
            String errorMsg = (resolvedCname != null)
                    ? "CNAME points to '" + resolvedCname + "' instead of '" + defaultServeDomain + "'."
                    : "CNAME record not detected yet. Please add a CNAME record pointing to " + defaultServeDomain + ".";
            domain.setVerificationError(errorMsg);
        }

        CustomDomain saved = customDomainRepository.save(domain);
        return toResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public DomainAvailabilityResponse checkDomainAvailability(Long userId, String rawDomain) {
        String normalizedDomain = normalizeDomain(rawDomain);
        if (!isValidDomain(normalizedDomain)) {
            return new DomainAvailabilityResponse(normalizedDomain, false,
                    "Invalid domain format. Please enter a valid domain or subdomain (e.g. links.example.com).");
        }

        if (normalizedDomain.equalsIgnoreCase(defaultServeDomain)) {
            return new DomainAvailabilityResponse(normalizedDomain, false,
                    "Cannot use '" + defaultServeDomain + "' because it is the system's default domain.");
        }

        Optional<CustomDomain> existingOpt = customDomainRepository.findByDomain(normalizedDomain);
        if (existingOpt.isPresent()) {
            CustomDomain existing = existingOpt.get();
            if (userId != null && userId.equals(existing.getUserId())) {
                return new DomainAvailabilityResponse(normalizedDomain, false,
                        "You have already added '" + normalizedDomain + "' to your account. You can verify it from your domains list.");
            } else {
                return new DomainAvailabilityResponse(normalizedDomain, false,
                        "Domain '" + normalizedDomain + "' is already in use by another account.");
            }
        }

        return new DomainAvailabilityResponse(normalizedDomain, true, "Domain is available.");
    }

    @Transactional(readOnly = true)
    public Page<CustomDomainResponse> listDomains(Long userId, Pageable pageable) {
        return customDomainRepository.findByUserId(userId, pageable)
                .map(d -> toResponse(d, false));
    }

    @Transactional(readOnly = true)
    public CustomDomainResponse getDefaultDomain() {
        return new CustomDomainResponse(
                0L,
                defaultServeDomain,
                DomainStatus.ACTIVE,
                defaultServeDomain,
                null,
                true,
                "Our default domain",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    @Transactional(readOnly = true)
    public List<CustomDomainResponse> listVerifiedDomains(Long userId) {
        if (userId == null) {
            return List.of(getDefaultDomain());
        }
        List<CustomDomainResponse> customVerified = customDomainRepository.findByUserIdAndStatus(userId, DomainStatus.ACTIVE)
                .stream()
                .map(d -> toResponse(d, false))
                .toList();

        List<CustomDomainResponse> result = new java.util.ArrayList<>();
        result.add(getDefaultDomain());
        result.addAll(customVerified);
        return result;
    }

    @Transactional
    public DomainVerificationResponse verifyDomain(Long userId, Long domainId) {
        CustomDomain domain = customDomainRepository.findByIdAndUserId(domainId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found with ID: " + domainId));

        String resolvedCname = dnsVerificationService.resolveCname(domain.getDomain());
        boolean isMatch = matchesCname(resolvedCname, domain.getCnameTarget());

        if (isMatch) {
            domain.setStatus(DomainStatus.ACTIVE);
            domain.setVerifiedAt(Instant.now());
            domain.setVerificationError(null);
            customDomainRepository.save(domain);
            log.info("Domain '{}' (ID {}) verified successfully for user {}", domain.getDomain(), domainId, userId);
            return new DomainVerificationResponse(true, domain.getDomain(), DomainStatus.ACTIVE, "Domain verified successfully!");
        } else {
            domain.setStatus(DomainStatus.VERIFICATION_REQUIRED);
            String errorMsg = (resolvedCname != null)
                    ? "CNAME points to '" + resolvedCname + "' instead of '" + domain.getCnameTarget() + "'."
                    : "No CNAME record found pointing to '" + domain.getCnameTarget() + "'. Please allow up to 24 hours for DNS propagation.";
            domain.setVerificationError(errorMsg);
            customDomainRepository.save(domain);
            log.warn("Domain '{}' (ID {}) verification failed for user {}: {}", domain.getDomain(), domainId, userId, errorMsg);
            return new DomainVerificationResponse(false, domain.getDomain(), DomainStatus.VERIFICATION_REQUIRED, errorMsg);
        }
    }

    @Transactional
    public boolean deleteDomain(Long userId, Long domainId) {
        return customDomainRepository.findByIdAndUserId(domainId, userId)
                .map(domain -> {
                    customDomainRepository.delete(domain);
                    log.info("Domain '{}' (ID {}) deleted by user {}", domain.getDomain(), domainId, userId);
                    return true;
                })
                .orElse(false);
    }

    public List<DnsInstructionDto> getDnsInstructions() {
        return List.of(
                new DnsInstructionDto(
                        "Cloudflare",
                        "CNAME",
                        "@ or subdomain (e.g. links, go)",
                        defaultServeDomain,
                        "Auto",
                        "Set Proxy status to 'DNS only' (gray cloud) during verification."
                ),
                new DnsInstructionDto(
                        "GoDaddy",
                        "CNAME",
                        "subdomain (e.g. links, go)",
                        defaultServeDomain,
                        "1 Hour (3600s)",
                        "Enter your subdomain as Host and point to " + defaultServeDomain + "."
                ),
                new DnsInstructionDto(
                        "Namecheap / Other",
                        "CNAME",
                        "subdomain (e.g. links, go)",
                        defaultServeDomain,
                        "Automatic / 1 Hour",
                        "Add a CNAME Record with Host pointing to " + defaultServeDomain + "."
                )
        );
    }

    public String normalizeDomain(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        // Strip protocol
        if (s.startsWith("http://")) s = s.substring(7);
        else if (s.startsWith("https://")) s = s.substring(8);
        // Strip path / query
        int slashIdx = s.indexOf('/');
        if (slashIdx != -1) s = s.substring(0, slashIdx);
        // Strip port
        int colonIdx = s.indexOf(':');
        if (colonIdx != -1) s = s.substring(0, colonIdx);
        // Strip trailing dot
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public boolean isValidDomain(String domain) {
        if (domain == null || domain.isBlank() || domain.length() > 255) {
            return false;
        }
        return DOMAIN_PATTERN.matcher(domain).matches();
    }

    private boolean matchesCname(String resolved, String expected) {
        if (resolved == null || expected == null) return false;
        String r = resolved.trim().toLowerCase();
        String e = expected.trim().toLowerCase();
        if (r.endsWith(".")) r = r.substring(0, r.length() - 1);
        if (e.endsWith(".")) e = e.substring(0, e.length() - 1);
        return r.equals(e);
    }

    private CustomDomainResponse toResponse(CustomDomain domain, boolean isDefault) {
        String description;
        if (isDefault) {
            description = "Our default domain";
        } else if (domain.getStatus() == DomainStatus.ACTIVE) {
            description = "Verified custom domain";
        } else if (domain.getStatus() == DomainStatus.PENDING) {
            description = "SSL provisioning in progress";
        } else {
            description = "DNS configuration required";
        }

        return new CustomDomainResponse(
                domain.getId(),
                domain.getDomain(),
                domain.getStatus(),
                domain.getCnameTarget(),
                domain.getVerificationError(),
                isDefault,
                description,
                domain.getCreatedAt(),
                domain.getVerifiedAt()
        );
    }
}
