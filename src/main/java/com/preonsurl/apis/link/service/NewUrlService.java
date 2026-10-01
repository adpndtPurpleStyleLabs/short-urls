package com.preonsurl.apis.link.service;

import com.preonsurl.apis.domain.entity.CustomDomain;
import com.preonsurl.apis.domain.entity.DomainStatus;
import com.preonsurl.apis.domain.repository.CustomDomainRepository;
import com.preonsurl.apis.link.cache.NewUrlLruCache;
import com.preonsurl.apis.link.dto.CreateRequest.CreateNewUrlRequest;
import com.preonsurl.apis.link.dto.CreateNewUrlResponse;
import com.preonsurl.apis.link.dto.EditNewUrlRequest;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.entity.NewUrlChangeLog;
import com.preonsurl.apis.link.entity.NewUrlTag;
import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.link.exception.ResourceNotFoundException;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlChangeLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.repository.NewUrlTagRepository;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.AccessPolicies;
import com.preonsurl.apis.link.dto.CreateRequest.UsagePolicies.UsagePolicies;
import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.UsagePolicy;
import com.preonsurl.apis.link.enums.AccessPolicyMode;
import com.preonsurl.apis.link.enums.UsagePolicyType;
import com.preonsurl.apis.link.repository.AccessPolicyRepository;
import com.preonsurl.apis.link.repository.UsagePolicyRepository;
import com.preonsurl.apis.link.exception.UrlExpiredException;
import com.preonsurl.apis.link.exception.UrlNotFoundException;
import com.preonsurl.apis.link.exception.UrlUsageLimitExceededException;
import com.preonsurl.core.ShortCodePool;
import com.preonsurl.coreconfig.constants.CoreConfigKeys;
import com.preonsurl.coreconfig.service.CoreConfigService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.CountryPolicy;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.DevicePolicy;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.IpAllowlistPolicy;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.OtpPolicy;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.PasswordPolicy;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.PinPolicy;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.ReferrerPolicy;
import com.preonsurl.apis.link.dto.CreateRequest.UsagePolicies.AccessSchedule;
import com.preonsurl.apis.link.dto.LinkRecipientDto;
import com.preonsurl.apis.link.entity.LinkRecipient;
import com.preonsurl.apis.link.repository.LinkRecipientRepository;
import com.preonsurl.emailer.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.preonsurl.apis.link.dto.UrlListItemResponse;
import com.preonsurl.apis.link.dto.LinkAccessLogResponse;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

@Service
public class NewUrlService {
    private static final Logger log = LoggerFactory.getLogger(NewUrlService.class);
    private static final Pattern CUSTOM_PATH_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*$");
    private final int DEFAULT_EXPIRE_YEARS = 10;
    private final NewUrlRepository repository;
    private final NewUrlAccessLogRepository accessLogRepository;
    private final NewUrlTagRepository tagRepository;
    private final NewUrlChangeLogRepository changeLogRepository;
    private final NewUrlLruCache lruCache;
    private final ShortCodePool codePool;
    private final AccessPolicyRepository accessPolicyRepository;
    private final UsagePolicyRepository usagePolicyRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomDomainRepository customDomainRepository;
    private final LinkRecipientRepository linkRecipientRepository;
    private final EmailService emailService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    public String domain = "";

    public NewUrlService(NewUrlRepository repository,
                         NewUrlAccessLogRepository accessLogRepository,
                         NewUrlTagRepository tagRepository,
                         NewUrlChangeLogRepository changeLogRepository,
                         NewUrlLruCache lruCache,
                         ShortCodePool codePool,
                         AccessPolicyRepository accessPolicyRepository,
                         UsagePolicyRepository usagePolicyRepository,
                         PasswordEncoder passwordEncoder,
                         CustomDomainRepository customDomainRepository,
                          LinkRecipientRepository linkRecipientRepository,
                         @Autowired(required = false) EmailService emailService,
                         @Autowired(required = false) com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                         CoreConfigService coreConfigService) {
        this.repository = repository;
        this.accessLogRepository = accessLogRepository;
        this.tagRepository = tagRepository;
        this.changeLogRepository = changeLogRepository;
        this.lruCache = lruCache;
        this.codePool = codePool;
        this.accessPolicyRepository = accessPolicyRepository;
        this.usagePolicyRepository = usagePolicyRepository;
        this.passwordEncoder = passwordEncoder;
        this.customDomainRepository = customDomainRepository;
        this.linkRecipientRepository = linkRecipientRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper != null ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        this.domain = coreConfigService.get(CoreConfigKeys.App.SECURE_URL);
    }

    private record EffectiveDomain(String domainName, String baseUrl) {}

    private String normalizeDomain(String rawDomain) {
        if (rawDomain == null) return "";
        String s = rawDomain.trim().toLowerCase();
        if (s.startsWith("http://")) {
            s = s.substring(7);
        } else if (s.startsWith("https://")) {
            s = s.substring(8);
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private EffectiveDomain resolveEffectiveDomain(String requestedDomain, Long userId) {
        if (requestedDomain == null || requestedDomain.isBlank()) {
            return new EffectiveDomain(null, this.domain);
        }

        final String normalized = normalizeDomain(requestedDomain);
        if (normalized.isBlank()) {
            return new EffectiveDomain(null, this.domain);
        }

        String defaultHost = extractHost(this.domain);
        if (normalized.equalsIgnoreCase(defaultHost) || normalized.equalsIgnoreCase(this.domain)) {
            return new EffectiveDomain(null, this.domain);
        }

        CustomDomain customDomain = customDomainRepository.findByDomain(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Domain '" + normalized + "' is not registered."));

        if (userId == null) {
            throw new IllegalArgumentException("Authentication required to use custom domains.");
        }

        if (!Objects.equals(customDomain.getUserId(), userId)) {
            throw new IllegalArgumentException("Domain '" + normalized + "' does not belong to your account.");
        }

        if (customDomain.getStatus() != DomainStatus.ACTIVE) {
            throw new IllegalArgumentException("Domain '" + normalized + "' is not verified yet. Please verify domain CNAME before creating branded links.");
        }

        return new EffectiveDomain(customDomain.getDomain(), "https://" + customDomain.getDomain());
    }

    private String extractHost(String domainUrl) {
        if (domainUrl == null) return "";
        String s = domainUrl.trim().toLowerCase();
        if (s.startsWith("http://")) s = s.substring(7);
        else if (s.startsWith("https://")) s = s.substring(8);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        return s;
    }

    @Transactional
    public CreateNewUrlResponse createNewUrl(CreateNewUrlRequest request, Long userId, String source) {
        if (request == null) {throw new IllegalArgumentException("Request cannot be null");}
        request.validate();

        String originalUrl = request.url();
        validateUrl(originalUrl);

        Instant expiresAt = request.resolvedExpireAt();
        if (expiresAt == null) {
            expiresAt = ZonedDateTime.now(ZoneOffset.UTC).plusYears(DEFAULT_EXPIRE_YEARS).toInstant();
        }

        LinkMode linkMode = request.resolvedLinkMode();
        String customPath = request.resolvedCustomPath();
        boolean addShortCode = request.resolvedAddShortCode();
        EffectiveDomain effectiveDomain = resolveEffectiveDomain(request.resolvedDomain(), userId);
        String baseDomainUrl = effectiveDomain.baseUrl();

        if (customPath != null) {
            if (addShortCode) {
                // Generate a new unique short code and append to customPath for every request
                String shortCode = generateUniqueShortCode();
                String fullPath = customPath + "/" + shortCode;
                String newUrl = baseDomainUrl + "/" + fullPath;

                NewUrl newUrlEntity = new NewUrl(shortCode, originalUrl, customPath, effectiveDomain.domainName(), newUrl, expiresAt, request.resolvedUsageLimit(), linkMode);
                newUrlEntity.setUserId(userId);
                newUrlEntity.setCreatedBy(source);
                if (request.notes() != null && !request.notes().isBlank()) {
                    newUrlEntity.setNote(request.notes().trim());
                }
                NewUrl saved = repository.save(newUrlEntity);
                changeLogRepository.save(new NewUrlChangeLog(saved.getId(), userId, "CREATED", "ALL", null, saved.getNewUrl()));

                List<String> savedTags = saveTags(saved.getId(), userId, request.tags());
                savePolicies(saved.getId(), request, saved.getExpireAt());

                return new CreateNewUrlResponse(
                        saved.getNewUrl(),
                        saved.getOriginalUrl(),
                        customPath,
                        false,
                        saved.getExpireAt(),
                        saved.getUsageLimit(),
                        saved.getNote(),
                        savedTags,
                        saved.getLinkMode(),
                        saved.isActive(),
                        saved.getPublicId(),
                        saved.getCreatedBy() != null ? saved.getCreatedBy() : source
                );
            } else {
                String fullPath = customPath;
                String shortCode = customPath;
                String newUrl = baseDomainUrl + "/" + fullPath;

                Optional<NewUrl> existingByPath = repository.findByNewUrl(newUrl);
                if (existingByPath.isEmpty()) {
                    existingByPath = repository.findByShortCode(shortCode)
                            .filter(e -> Objects.equals(e.getDomain(), effectiveDomain.domainName()));
                }

                if (existingByPath.isPresent()) {
                    NewUrl existing = existingByPath.get();
                    if (!existing.getOriginalUrl().equals(originalUrl)) {
                        throw new IllegalArgumentException("Custom path '" + customPath + "' is already in use");
                    }

                    if (isUsable(existing)) {
                        if (existing.getUserId() == null && userId != null) {
                            existing.setUserId(userId);
                            repository.save(existing);
                        }
                        if (existing.getNote() == null && request.notes() != null && !request.notes().isBlank()) {
                            existing.setNote(request.notes().trim());
                            repository.save(existing);
                        }
                        if (existing.getLinkMode() != linkMode && request.linkMode() != null) {
                            existing.setLinkMode(linkMode);
                            repository.save(existing);
                        }
                        if (existing.getCreatedBy() == null || existing.getCreatedBy().isBlank()) {
                            existing.setCreatedBy(source);
                            repository.save(existing);
                        }
                        List<String> tags = saveTags(existing.getId(), userId, request.tags());
                        if (tags.isEmpty()) {
                            tags = tagRepository.findByUrlId(existing.getId()).stream().map(NewUrlTag::getTag).toList();
                        }
                        savePolicies(existing.getId(), request, existing.getExpireAt());
                        return new CreateNewUrlResponse(
                                existing.getNewUrl(),
                                existing.getOriginalUrl(),
                                customPath,
                                true,
                                existing.getExpireAt(),
                                existing.getUsageLimit(),
                                existing.getNote(),
                                tags,
                                existing.getLinkMode(),
                                existing.isActive(),
                                existing.getPublicId(),
                                existing.getCreatedBy() != null ? existing.getCreatedBy() : source
                        );
                    }

                    // Condition exhausted: renew/reactivate the existing custom path entry
                    existing.setActive(true);
                    existing.setExpireAt(expiresAt);
                    existing.setUsageLimit(request.resolvedUsageLimit());
                    existing.setClickCount(0);
                    existing.setDomain(effectiveDomain.domainName());
                    existing.setNewUrl(newUrl);
                    existing.setCreatedBy(source);
                    if (userId != null) {
                        existing.setUserId(userId);
                    }
                    if (request.notes() != null && !request.notes().isBlank()) {
                        existing.setNote(request.notes().trim());
                    }
                    if (request.linkMode() != null) {
                        existing.setLinkMode(linkMode);
                    }
                    NewUrl saved = repository.save(existing);
                    changeLogRepository.save(new NewUrlChangeLog(saved.getId(), userId, "EDITED", "REACTIVATED", null, saved.getNewUrl()));
                    List<String> savedTags = saveTags(saved.getId(), userId, request.tags());
                    savePolicies(saved.getId(), request, saved.getExpireAt());

                    return new CreateNewUrlResponse(
                            saved.getNewUrl(),
                            saved.getOriginalUrl(),
                            customPath,
                            false,
                            saved.getExpireAt(),
                            saved.getUsageLimit(),
                            saved.getNote(),
                            savedTags,
                            saved.getLinkMode(),
                            saved.isActive(),
                            saved.getPublicId(),
                            buildUsagePoliciesDto(saved),
                            buildAccessPoliciesDto(saved.getId()),
                            saved.getCreatedBy() != null ? saved.getCreatedBy() : source
                    );
                }

                // Save to database
                NewUrl newUrlEntity = new NewUrl(shortCode, originalUrl, customPath, effectiveDomain.domainName(), newUrl, expiresAt, request.resolvedUsageLimit(), linkMode);
                newUrlEntity.setUserId(userId);
                newUrlEntity.setCreatedBy(source);
                if (request.notes() != null && !request.notes().isBlank()) {
                    newUrlEntity.setNote(request.notes().trim());
                }
                NewUrl saved = repository.save(newUrlEntity);
                changeLogRepository.save(new NewUrlChangeLog(saved.getId(), userId, "CREATED", "ALL", null, saved.getNewUrl()));

                List<String> savedTags = saveTags(saved.getId(), userId, request.tags());
                savePolicies(saved.getId(), request, saved.getExpireAt());

                return new CreateNewUrlResponse(
                        saved.getNewUrl(),
                        saved.getOriginalUrl(),
                        customPath,
                        false,
                        saved.getExpireAt(),
                        saved.getUsageLimit(),
                        saved.getNote(),
                        savedTags,
                        saved.getLinkMode(),
                        saved.isActive(),
                        saved.getPublicId(),
                        buildUsagePoliciesDto(saved),
                        buildAccessPoliciesDto(saved.getId()),
                        saved.getCreatedBy() != null ? saved.getCreatedBy() : source
                );
            }
        } else {
                // Generate a new unique short code for every request
                String code = generateUniqueShortCode();
                String newUrl = baseDomainUrl + "/" + code;

            NewUrl newUrlEntity = new NewUrl(code, originalUrl, null, effectiveDomain.domainName(), newUrl, expiresAt, request.resolvedUsageLimit(), linkMode);
            newUrlEntity.setUserId(userId);
            newUrlEntity.setCreatedBy(source);
            if (request.notes() != null && !request.notes().isBlank()) {
                newUrlEntity.setNote(request.notes().trim());
            }
            NewUrl saved = repository.save(newUrlEntity);
            changeLogRepository.save(new NewUrlChangeLog(saved.getId(), userId, "CREATED", "ALL", null, saved.getNewUrl()));

            List<String> savedTags = saveTags(saved.getId(), userId, request.tags());
            savePolicies(saved.getId(), request, saved.getExpireAt());

            return new CreateNewUrlResponse(
                    saved.getNewUrl(),
                    saved.getOriginalUrl(),
                    null,
                    false,
                    saved.getExpireAt(),
                    saved.getUsageLimit(),
                    saved.getNote(),
                    savedTags,
                    saved.getLinkMode(),
                    saved.isActive(),
                    saved.getPublicId(),
                    buildUsagePoliciesDto(saved),
                    buildAccessPoliciesDto(saved.getId()),
                    saved.getCreatedBy() != null ? saved.getCreatedBy() : source
            );
        }
    }

    public boolean isUsable(NewUrl url) {
        if (url == null) {
            return false;
        }
        if (!url.isActive()) {
            return false;
        }
        if (url.getExpireAt() != null && !Instant.now().isBefore(url.getExpireAt())) {
            return false;
        }
        if (url.getUsageLimit() != null && url.getClickCount() >= url.getUsageLimit()) {
            return false;
        }
        return true;
    }

    private List<String> saveTags(Long urlId, Long userId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> distinctTags = tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        for (String tag : distinctTags) {
            if (!tagRepository.existsByUrlIdAndTag(urlId, tag)) {
                tagRepository.save(new NewUrlTag(urlId, userId, tag));
            }
        }
        return distinctTags;
    }

    public UsagePolicies buildUsagePoliciesDto(NewUrl newUrl) {
        return usagePolicyRepository.findByShortUrlId(newUrl.getId())
                .map(up -> new UsagePolicies(
                        up.getPolicyType(),
                        up.getUsageLimit(),
                        up.getExpireAt(),
                        (up.getStartAt() != null && up.getEndAt() != null)
                                ? new AccessSchedule(up.getStartAt(), up.getEndAt())
                                : null
                ))
                .orElseGet(() -> new UsagePolicies(
                        newUrl.getUsageLimit() != null ? (newUrl.getUsageLimit() == 1 ? UsagePolicyType.ONE_TIME : UsagePolicyType.USAGE_LIMIT) : UsagePolicyType.UNLIMITED,
                        newUrl.getUsageLimit(),
                        newUrl.getExpireAt(),
                        null
                ));
    }

    public AccessPolicies buildAccessPoliciesDto(Long urlId) {
        return accessPolicyRepository.findByShortUrlId(urlId)
                .map(ap -> new AccessPolicies(
                        ap.getMode(),
                        ap.hasPin() ? new PinPolicy("******") : null,
                        ap.hasPassword() ? new PasswordPolicy("******") : null,
                        (ap.getIpAllowlist() != null && !ap.getIpAllowlist().isBlank())
                                ? new IpAllowlistPolicy(Arrays.stream(ap.getIpAllowlist().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())
                                : null,
                        (ap.getCountries() != null && !ap.getCountries().isBlank())
                                ? new CountryPolicy(Arrays.stream(ap.getCountries().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())
                                : null,
                        (ap.getDeviceTypes() != null && !ap.getDeviceTypes().isBlank())
                                ? new DevicePolicy(Arrays.stream(ap.getDeviceTypes().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())
                                : null,
                        (ap.getReferrers() != null && !ap.getReferrers().isBlank())
                                ? new ReferrerPolicy(Arrays.stream(ap.getReferrers().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())
                                : null,
                        ap.hasOtp()
                                ? new OtpPolicy(true, (ap.getOtpEmails() != null && !ap.getOtpEmails().isBlank())
                                ? Arrays.stream(ap.getOtpEmails().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                                : List.of(), false)
                                : null,
                        parseGeoFencePolicy(ap.getGeofence())
                ))
                .orElseGet(AccessPolicies::publicAccess);
    }

    private com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.GeoFencePolicy parseGeoFencePolicy(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.GeoFencePolicy.class);
        } catch (Exception e) {
            log.debug("Failed to deserialize geofence policy JSON: {}", e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public CreateNewUrlResponse getLinkInfo(String publicId, Long userId) {
        String trimmedPublicId = publicId.trim();
        Optional<NewUrl> found = repository.findByPublicIdAndUserId(trimmedPublicId, userId);
        if (found.isEmpty()) {
            found = repository.findByShortCodeAndUserId(trimmedPublicId, userId);
        }
        if (found.isEmpty()) {
            found = repository.findByNewUrlAndUserId(trimmedPublicId, userId);
        }
        if (found.isEmpty() && !trimmedPublicId.startsWith("http://") && !trimmedPublicId.startsWith("https://")) {
            String prefixed = domain + (trimmedPublicId.startsWith("/") ? "" : "/") + trimmedPublicId;
            found = repository.findByNewUrlAndUserId(prefixed, userId);
        }

        NewUrl newUrl = found.orElseThrow(() -> new UrlNotFoundException("URL not found"));

        List<String> tags = tagRepository.findByUrlId(newUrl.getId()).stream()
                .map(NewUrlTag::getTag)
                .toList();

        UsagePolicies usagePolicies = buildUsagePoliciesDto(newUrl);
        AccessPolicies accessPolicies = buildAccessPoliciesDto(newUrl.getId());

        return new CreateNewUrlResponse(
                newUrl.getNewUrl(),
                newUrl.getOriginalUrl(),
                newUrl.getCustomPath(),
                true,
                newUrl.getExpireAt(),
                newUrl.getUsageLimit(),
                newUrl.getNote(),
                tags,
                newUrl.getLinkMode(),
                newUrl.isActive(),
                newUrl.getPublicId(),
                usagePolicies,
                accessPolicies,
                newUrl.getCreatedBy() != null ? newUrl.getCreatedBy() : "UI"
        );
    }

    private NewUrl findEntityForEdit(EditNewUrlRequest request, Long userId) {
        if (request.publicId() != null && !request.publicId().isBlank()) {
            String trimmedPublicId = request.publicId().trim();
            Optional<NewUrl> found = repository.findByPublicIdAndUserId(trimmedPublicId, userId);
            if (found.isPresent()) {
                return found.get();
            }
        }

        if (request.newUrl() != null && !request.newUrl().isBlank()) {
            String trimmedUrl = request.newUrl().trim();

            Optional<NewUrl> found = repository.findByPublicIdAndUserId(trimmedUrl, userId);
            if (found.isPresent()) {
                return found.get();
            }

            found = repository.findByNewUrlAndUserId(trimmedUrl, userId);
            if (found.isPresent()) {
                return found.get();
            }

            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                String prefixed = domain + (trimmedUrl.startsWith("/") ? "" : "/") + trimmedUrl;
                found = repository.findByNewUrlAndUserId(prefixed, userId);
                if (found.isPresent()) {
                    return found.get();
                }
            }

            found = repository.findByShortCodeAndUserId(trimmedUrl, userId);
            if (found.isPresent()) {
                return found.get();
            }
        }

        if (request.shortCode() != null && !request.shortCode().isBlank()) {
            String trimmedCode = request.shortCode().trim();
            Optional<NewUrl> found = repository.findByShortCodeAndUserId(trimmedCode, userId);
            if (found.isPresent()) {
                return found.get();
            }
        }

        throw new UrlNotFoundException("Link not found or access denied");
    }

    @Transactional
    public CreateNewUrlResponse editNewUrl(EditNewUrlRequest request, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("User ID is required to edit link");
        }
        if (request == null || !request.hasIdentifier()) {
            throw new IllegalArgumentException("URL parameter 'newUrl' or 'id'/'publicId' cannot be empty");
        }

        NewUrl entity = findEntityForEdit(request, userId);
        final Long shortUrlId = entity.getId();

        LinkMode finalMode = request.linkMode() != null ? request.linkMode() : entity.getLinkMode();
        String finalUrl = (request.originalUrl() != null && !request.originalUrl().isBlank())
                ? request.originalUrl().trim()
                : entity.getOriginalUrl();
        if (finalMode == LinkMode.PROXY || finalMode == LinkMode.MIRROR) {
            ProxyResourceValidator.validateProxyUrl(finalUrl);
        }

        boolean modified = false;

        // A. Destination URL (originalUrl)
        if (request.originalUrl() != null && !request.originalUrl().isBlank()) {
            String newOriginalUrl = request.originalUrl().trim();
            validateUrl(newOriginalUrl);
            if (!Objects.equals(entity.getOriginalUrl(), newOriginalUrl)) {
                String oldVal = entity.getOriginalUrl();
                entity.setOriginalUrl(newOriginalUrl);
                entity.setOriginalUrlHash(NewUrl.hashUrl(newOriginalUrl));
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "original_url", oldVal, newOriginalUrl));
                modified = true;
            }
        }

        // B. Custom Domain & Slug/Path
        EffectiveDomain targetDomain;
        if (request.domain() != null) {
            targetDomain = resolveEffectiveDomain(request.domain(), userId);
        } else {
            String currentDomainName = entity.getDomain();
            String currentBaseUrl = (currentDomainName != null && !currentDomainName.isBlank())
                    ? "https://" + currentDomainName
                    : this.domain;
            targetDomain = new EffectiveDomain(currentDomainName, currentBaseUrl);
        }

        String targetCustomPath = entity.getCustomPath();
        String targetShortCode = entity.getShortCode();

        if (request.customPath() != null) {
            String rawPath = request.customPath().trim();
            while (rawPath.startsWith("/")) rawPath = rawPath.substring(1).trim();
            while (rawPath.endsWith("/")) rawPath = rawPath.substring(0, rawPath.length() - 1).trim();
            if (rawPath.isEmpty()) {
                targetCustomPath = null;
                if (Objects.equals(entity.getShortCode(), entity.getCustomPath())) {
                    targetShortCode = generateUniqueShortCode();
                }
            } else {
                if (rawPath.length() > 64) {
                    throw new IllegalArgumentException("customPath cannot exceed 64 characters");
                }
                if (!CUSTOM_PATH_PATTERN.matcher(rawPath).matches()) {
                    throw new IllegalArgumentException("Invalid customPath format: '" + rawPath + "'");
                }
                targetCustomPath = rawPath;
                targetShortCode = rawPath;
            }
        }

        if (request.shortCode() != null && !request.shortCode().isBlank()) {
            String rawCode = request.shortCode().trim();
            if (rawCode.length() > 64) {
                throw new IllegalArgumentException("shortCode cannot exceed 64 characters");
            }
            if (!CUSTOM_PATH_PATTERN.matcher(rawCode).matches()) {
                throw new IllegalArgumentException("Invalid shortCode format: '" + rawCode + "'");
            }
            targetShortCode = rawCode;
            if (request.customPath() == null && entity.getCustomPath() != null && Objects.equals(entity.getCustomPath(), entity.getShortCode())) {
                targetCustomPath = rawCode;
            }
        }

        String targetSlug = targetCustomPath != null ? targetCustomPath : targetShortCode;
        String targetNewUrl = targetDomain.baseUrl() + "/" + targetSlug;

        // Collision checks
        if (!Objects.equals(entity.getShortCode(), targetShortCode)) {
            Optional<NewUrl> codeCollision = repository.findByShortCode(targetShortCode);
            if (codeCollision.isPresent() && !Objects.equals(codeCollision.get().getId(), entity.getId())) {
                throw new IllegalArgumentException("Short code or custom path '" + targetShortCode + "' is already in use");
            }
        }

        if (!Objects.equals(entity.getNewUrl(), targetNewUrl)) {
            Optional<NewUrl> urlCollision = repository.findByNewUrl(targetNewUrl);
            if (urlCollision.isPresent() && !Objects.equals(urlCollision.get().getId(), entity.getId())) {
                throw new IllegalArgumentException("URL '" + targetNewUrl + "' is already in use");
            }
        }

        if (!Objects.equals(entity.getCustomPath(), targetCustomPath)) {
            String oldVal = entity.getCustomPath();
            entity.setCustomPath(targetCustomPath);
            changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "custom_path", oldVal, targetCustomPath));
            modified = true;
        }

        if (!Objects.equals(entity.getShortCode(), targetShortCode)) {
            String oldVal = entity.getShortCode();
            entity.setShortCode(targetShortCode);
            changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "short_code", oldVal, targetShortCode));
            modified = true;
        }

        if (!Objects.equals(entity.getDomain(), targetDomain.domainName())) {
            String oldVal = entity.getDomain();
            entity.setDomain(targetDomain.domainName());
            changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "domain", oldVal, targetDomain.domainName()));
            modified = true;
        }

        if (!Objects.equals(entity.getNewUrl(), targetNewUrl)) {
            String oldVal = entity.getNewUrl();
            lruCache.remove(oldVal);
            entity.setNewUrl(targetNewUrl);
            changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "new_url", oldVal, targetNewUrl));
            modified = true;
        }

        // C. Click Count / Reset
        if (Boolean.TRUE.equals(request.resetClickCount()) || request.clickCount() != null) {
            long targetClicks = Boolean.TRUE.equals(request.resetClickCount()) ? 0L : request.clickCount();
            if (targetClicks < 0) {
                throw new IllegalArgumentException("clickCount cannot be negative");
            }
            if (entity.getClickCount() != targetClicks) {
                String oldVal = String.valueOf(entity.getClickCount());
                String newVal = String.valueOf(targetClicks);
                entity.setClickCount(targetClicks);
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "click_count", oldVal, newVal));
                modified = true;

                usagePolicyRepository.findByShortUrlId(shortUrlId).ifPresent(up -> {
                    up.setCurrentUsage(targetClicks);
                    usagePolicyRepository.save(up);
                });
            }
        }

        // D. Usage Policies & Expiration
        if (request.hasUsagePolicies()) {
            UsagePolicies upReq = request.usagePolicies();
            UsagePolicyType newType = upReq.resolvedType();
            Long newUsageLimit = upReq.resolvedUsageLimit();
            Instant newExpireAt = upReq.expireAt();
            AccessSchedule schedule = upReq.schedule();

            if (newExpireAt == null && newType == UsagePolicyType.UNLIMITED) {
                newExpireAt = Instant.now().plus(3650, ChronoUnit.DAYS);
            } else if (newExpireAt != null && !newExpireAt.isAfter(Instant.now())) {
                throw new IllegalArgumentException("expireAt must be in the future");
            }

            if (!Objects.equals(entity.getUsageLimit(), newUsageLimit)) {
                String oldVal = entity.getUsageLimit() != null ? entity.getUsageLimit().toString() : null;
                String newVal = newUsageLimit != null ? newUsageLimit.toString() : null;
                entity.setUsageLimit(newUsageLimit);
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "usage_limit", oldVal, newVal));
                modified = true;
            }

            if (newExpireAt != null && !Objects.equals(entity.getExpireAt(), newExpireAt)) {
                String oldVal = entity.getExpireAt() != null ? entity.getExpireAt().toString() : null;
                String newVal = newExpireAt.toString();
                entity.setExpireAt(newExpireAt);
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "expire_at", oldVal, newVal));
                modified = true;
            }

            final Instant effectiveExpireAt = newExpireAt != null ? newExpireAt : entity.getExpireAt();
            UsagePolicy up = usagePolicyRepository.findByShortUrlId(shortUrlId)
                    .orElseGet(() -> new UsagePolicy(shortUrlId, newType, newUsageLimit, effectiveExpireAt, null, null));
            up.setPolicyType(newType);
            up.setUsageLimit(newUsageLimit);
            up.setExpireAt(effectiveExpireAt);
            if (schedule != null) {
                schedule.validate();
                up.setStartAt(schedule.startAt());
                up.setEndAt(schedule.endAt());
            } else {
                up.setStartAt(null);
                up.setEndAt(null);
            }
            usagePolicyRepository.save(up);
        } else {
            // Legacy expiration & usageLimit
            if (Boolean.TRUE.equals(request.clearExpireAt())) {
                Instant defaultExpire = Instant.now().plus(3650, ChronoUnit.DAYS);
                if (!Objects.equals(entity.getExpireAt(), defaultExpire)) {
                    String oldVal = entity.getExpireAt() != null ? entity.getExpireAt().toString() : null;
                    entity.setExpireAt(defaultExpire);
                    changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "expire_at", oldVal, defaultExpire.toString()));
                    modified = true;
                    usagePolicyRepository.findByShortUrlId(shortUrlId).ifPresent(up -> {
                        up.setExpireAt(defaultExpire);
                        usagePolicyRepository.save(up);
                    });
                }
            } else if (request.expireAt() != null) {
                if (!request.expireAt().isAfter(Instant.now())) {
                    throw new IllegalArgumentException("expireAt must be in the future");
                }
                if (!Objects.equals(entity.getExpireAt(), request.expireAt())) {
                    String oldVal = entity.getExpireAt() != null ? entity.getExpireAt().toString() : null;
                    entity.setExpireAt(request.expireAt());
                    changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "expire_at", oldVal, request.expireAt().toString()));
                    modified = true;
                    usagePolicyRepository.findByShortUrlId(shortUrlId).ifPresent(up -> {
                        up.setExpireAt(request.expireAt());
                        usagePolicyRepository.save(up);
                    });
                }
            }

            if (request.hasUsageLimit()) {
                Long newUsageLimit = request.resolvedUsageLimit();
                if (!Objects.equals(entity.getUsageLimit(), newUsageLimit)) {
                    String oldVal = entity.getUsageLimit() != null ? entity.getUsageLimit().toString() : null;
                    String newVal = newUsageLimit != null ? newUsageLimit.toString() : null;
                    entity.setUsageLimit(newUsageLimit);
                    changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "usage_limit", oldVal, newVal));
                    modified = true;
                    usagePolicyRepository.findByShortUrlId(shortUrlId).ifPresent(up -> {
                        up.setUsageLimit(newUsageLimit);
                        up.setPolicyType(newUsageLimit != null ? (newUsageLimit == 1 ? UsagePolicyType.ONE_TIME : UsagePolicyType.USAGE_LIMIT) : UsagePolicyType.UNLIMITED);
                        usagePolicyRepository.save(up);
                    });
                }
            }
        }

        // E. Access Policies
        if (request.hasAccessPolicies()) {
            AccessPolicies apReq = request.accessPolicies();
            AccessPolicy ap = accessPolicyRepository.findByShortUrlId(shortUrlId)
                    .orElseGet(() -> new AccessPolicy(shortUrlId, apReq.mode() != null ? apReq.mode() : AccessPolicyMode.PUBLIC));

            AccessPolicyMode mode = apReq.mode() != null ? apReq.mode() : AccessPolicyMode.PUBLIC;
            ap.setMode(mode);

            if (mode == AccessPolicyMode.SECURED) {
                // PIN
                if (apReq.pin() != null && apReq.pin().pin() != null && !apReq.pin().pin().isBlank()) {
                    String pinVal = apReq.pin().pin().trim();
                    if (!"******".equals(pinVal)) {
                        ap.setPinHash(passwordEncoder.encode(pinVal));
                    }
                } else {
                    ap.setPinHash(null);
                }

                // Password
                if (apReq.password() != null && apReq.password().password() != null && !apReq.password().password().isBlank()) {
                    String passVal = apReq.password().password();
                    if (!"******".equals(passVal)) {
                        ap.setPasswordHash(passwordEncoder.encode(passVal));
                    }
                } else {
                    ap.setPasswordHash(null);
                }

                // IP Allowlist
                if (apReq.ipAllowlist() != null && apReq.ipAllowlist().addresses() != null && !apReq.ipAllowlist().addresses().isEmpty()) {
                    ap.setIpAllowlist(String.join(",", apReq.ipAllowlist().addresses()));
                } else {
                    ap.setIpAllowlist(null);
                }

                // Countries
                if (apReq.country() != null && apReq.country().countries() != null && !apReq.country().countries().isEmpty()) {
                    ap.setCountries(String.join(",", apReq.country().countries()));
                } else {
                    ap.setCountries(null);
                }

                // Devices
                if (apReq.device() != null && apReq.device().devices() != null && !apReq.device().devices().isEmpty()) {
                    ap.setDeviceTypes(String.join(",", apReq.device().devices()));
                } else {
                    ap.setDeviceTypes(null);
                }

                // Referrers
                if (apReq.referrer() != null && apReq.referrer().referrers() != null && !apReq.referrer().referrers().isEmpty()) {
                    ap.setReferrers(String.join(",", apReq.referrer().referrers()));
                } else {
                    ap.setReferrers(null);
                }

                // Email OTP
                if (apReq.otp() != null && apReq.otp().isEnabled()) {
                    ap.setOtpEnabled(true);
                    List<String> emails = apReq.otp().emails() != null
                            ? apReq.otp().emails().stream().map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).distinct().toList()
                            : List.of();
                    ap.setOtpEmails(String.join(",", emails));

                    linkRecipientRepository.deleteByShortUrlId(shortUrlId);

                    String linkUrl = entity.getNewUrl();
                    boolean shouldSend = apReq.otp().shouldSendEmails();

                    for (String email : emails) {
                        String token = UUID.randomUUID().toString().replace("-", "");
                        LinkRecipient recipient = new LinkRecipient(shortUrlId, email, token);
                        if (shouldSend && !linkUrl.isBlank() && emailService != null) {
                            try {
                                String trackingPixelUrl = this.domain + "/api/track/email-open/" + token;
                                emailService.sendSecuredLinkInvitation(email, linkUrl, trackingPixelUrl);
                                recipient.setEmailSent(true);
                                recipient.setEmailSentAt(Instant.now());
                            } catch (Exception e) {
                                log.warn("Failed to dispatch invitation email to '{}': {}", email, e.getMessage());
                            }
                        }
                        linkRecipientRepository.save(recipient);
                    }
                } else {
                    ap.setOtpEnabled(false);
                    ap.setOtpEmails(null);
                    linkRecipientRepository.deleteByShortUrlId(shortUrlId);
                }

                // Geofence
                if (apReq.geofence() != null && apReq.geofence().isValid()) {
                    try {
                        ap.setGeofence(objectMapper.writeValueAsString(apReq.geofence()));
                    } catch (Exception e) {
                        log.warn("Failed to serialize geofence policy: {}", e.getMessage());
                    }
                } else {
                    ap.setGeofence(null);
                }

                boolean hasAnyRestriction = ap.hasPin()
                        || ap.hasPassword()
                        || (ap.getIpAllowlist() != null && !ap.getIpAllowlist().isBlank())
                        || (ap.getCountries() != null && !ap.getCountries().isBlank())
                        || (ap.getDeviceTypes() != null && !ap.getDeviceTypes().isBlank())
                        || (ap.getReferrers() != null && !ap.getReferrers().isBlank())
                        || ap.isOtpEnabled()
                        || (ap.getGeofence() != null && !ap.getGeofence().isBlank());

                if (!hasAnyRestriction) {
                    throw new IllegalArgumentException(
                            "Secured access policy requires at least one restriction configured (pin, password, otp, ipAllowlist, country, device, referrer, or geofence)"
                    );
                }
            } else {
                // Mode is PUBLIC: wipe all security restrictions
                ap.setPinHash(null);
                ap.setPasswordHash(null);
                ap.setIpAllowlist(null);
                ap.setCountries(null);
                ap.setDeviceTypes(null);
                ap.setReferrers(null);
                ap.setOtpEnabled(false);
                ap.setOtpEmails(null);
                linkRecipientRepository.deleteByShortUrlId(shortUrlId);
                ap.setGeofence(null);
            }

            accessPolicyRepository.save(ap);
            changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "access_policies", null, mode.name()));
        }

        // F. notes
        if (request.notes() != null) {
            String newNote = request.notes().trim();
            if (newNote.isBlank()) {
                newNote = null;
            }
            if (!Objects.equals(entity.getNote(), newNote)) {
                String oldVal = entity.getNote();
                entity.setNote(newNote);
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "notes", oldVal, newNote));
                modified = true;
            }
        }

        // G. linkMode
        if (request.linkMode() != null) {
            if (!Objects.equals(entity.getLinkMode(), request.linkMode())) {
                String oldVal = entity.getLinkMode() != null ? entity.getLinkMode().name() : null;
                entity.setLinkMode(request.linkMode());
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "link_mode", oldVal, request.linkMode().name()));
                modified = true;
            }
        }

        // H. isActive
        if (request.isActive() != null) {
            if (entity.isActive() != request.isActive()) {
                String oldVal = String.valueOf(entity.isActive());
                String newVal = String.valueOf(request.isActive());
                entity.setActive(request.isActive());
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "is_active", oldVal, newVal));
                modified = true;
            }
        }

        // I. tags
        if (request.tags() != null) {
            List<String> currentTags = tagRepository.findByUrlId(entity.getId()).stream()
                    .map(NewUrlTag::getTag)
                    .sorted()
                    .toList();
            List<String> updatedTags = request.tags().stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .toList();

            if (!currentTags.equals(updatedTags)) {
                String oldVal = currentTags.isEmpty() ? null : String.join(", ", currentTags);
                String newVal = updatedTags.isEmpty() ? null : String.join(", ", updatedTags);
                tagRepository.deleteByUrlId(entity.getId());
                saveTags(entity.getId(), userId, updatedTags);
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "tags", oldVal, newVal));
                modified = true;
            }
        }

        if (modified) {
            entity = repository.save(entity);
            lruCache.remove(entity.getNewUrl());
        }

        List<String> finalTags = tagRepository.findByUrlId(entity.getId()).stream()
                .map(NewUrlTag::getTag)
                .toList();

        UsagePolicies finalUsagePolicies = buildUsagePoliciesDto(entity);
        AccessPolicies finalAccessPolicies = buildAccessPoliciesDto(entity.getId());

        return new CreateNewUrlResponse(
                entity.getNewUrl(),
                entity.getOriginalUrl(),
                entity.getCustomPath(),
                false,
                entity.getExpireAt(),
                entity.getUsageLimit(),
                entity.getNote(),
                finalTags,
                entity.getLinkMode(),
                entity.isActive(),
                entity.getPublicId(),
                finalUsagePolicies,
                finalAccessPolicies,
                entity.getCreatedBy() != null ? entity.getCreatedBy() : "UI",
                entity.getDomain(),
                entity.getShortCode(),
                entity.getClickCount()
        );
    }

    @Transactional
    public Optional<String> resolveAndRecordClick(String shortCode, String ipAddress, String userAgent, String referer) {
        Optional<NewUrl> optionalShortUrl = repository.findByShortCode(shortCode);

        if (optionalShortUrl.isPresent()) {
            NewUrl entity = optionalShortUrl.get();

            if (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt())) {
                log.warn("Short URL expired: code='{}', expireAt='{}'", shortCode, entity.getExpireAt());
                throw new UrlExpiredException("Short URL has expired");
            }

            if (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit()) {
                log.warn("Short URL usage limit reached: code='{}', clickCount='{}', usageLimit='{}'",
                        shortCode, entity.getClickCount(), entity.getUsageLimit());
                throw new UrlUsageLimitExceededException("Short URL usage limit reached");
            }

            repository.incrementClickCount(entity.getId());

            NewUrlAccessLog accessLog = new NewUrlAccessLog(
                    entity.getId(),
                    entity.getShortCode(),
                    ipAddress,
                    userAgent,
                    referer
            );
            accessLogRepository.save(accessLog);

            return Optional.of(entity.getOriginalUrl());
        }

        return Optional.empty();
    }

    @Transactional
    public Optional<String> resolveAndRecordClick(String shortCode) {
        return resolveAndRecordClick(shortCode, null, null, null);
    }

    private String generateUniqueShortCode() {
        for (int attempts = 0; attempts < 5; attempts++) {
            try {
                String code = codePool.nextCode(5, TimeUnit.SECONDS);
                if (!repository.existsByShortCode(code)) {
                    return code;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Thread interrupted while waiting for short code permit", e);
            } catch (TimeoutException e) {
                throw new IllegalStateException("Timeout acquiring short code permit", e);
            }
        }
        throw new IllegalStateException("Unable to generate unique short code after 5 attempts");
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Destination URL cannot be empty");
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("Only http:// and https:// URLs are supported");
        }
    }

    private void savePolicies(Long shortUrlId, CreateNewUrlRequest request, Instant expiresAt) {
        UsagePolicies usagePolicies = request.resolvedUsagePolicies();
        AccessPolicies accessPolicies = request.resolvedAccessPolicies();

        // 1. Usage Policy
        UsagePolicy usagePolicy = usagePolicyRepository.findByShortUrlId(shortUrlId)
                .orElseGet(() -> new UsagePolicy(
                        shortUrlId,
                        usagePolicies.resolvedType(),
                        usagePolicies.resolvedUsageLimit(),
                        expiresAt,
                        usagePolicies.resolvedSchedule() != null ? usagePolicies.resolvedSchedule().startAt() : null,
                        usagePolicies.resolvedSchedule() != null ? usagePolicies.resolvedSchedule().endAt() : null
                ));
        usagePolicy.setPolicyType(usagePolicies.resolvedType());
        usagePolicy.setUsageLimit(usagePolicies.resolvedUsageLimit());
        usagePolicy.setExpireAt(expiresAt);
        if (usagePolicies.resolvedSchedule() != null) {
            usagePolicy.setStartAt(usagePolicies.resolvedSchedule().startAt());
            usagePolicy.setEndAt(usagePolicies.resolvedSchedule().endAt());
        } else {
            usagePolicy.setStartAt(null);
            usagePolicy.setEndAt(null);
        }
        usagePolicyRepository.save(usagePolicy);

        // 2. Access Policy
        AccessPolicy accessPolicy = accessPolicyRepository.findByShortUrlId(shortUrlId)
                .orElseGet(() -> new AccessPolicy(
                        shortUrlId,
                        accessPolicies.mode() != null ? accessPolicies.mode() : AccessPolicyMode.PUBLIC
                ));
        accessPolicy.setMode(accessPolicies.mode() != null ? accessPolicies.mode() : AccessPolicyMode.PUBLIC);

        if (accessPolicies.pin() != null && accessPolicies.pin().pin() != null && !accessPolicies.pin().pin().isBlank()) {
            accessPolicy.setPinHash(passwordEncoder.encode(accessPolicies.pin().pin().trim()));
        } else if (accessPolicies.isPublic()) {
            accessPolicy.setPinHash(null);
        }

        if (accessPolicies.password() != null && accessPolicies.password().password() != null && !accessPolicies.password().password().isBlank()) {
            accessPolicy.setPasswordHash(passwordEncoder.encode(accessPolicies.password().password()));
        } else if (accessPolicies.isPublic()) {
            accessPolicy.setPasswordHash(null);
        }

        if (accessPolicies.ipAllowlist() != null && accessPolicies.ipAllowlist().addresses() != null) {
            accessPolicy.setIpAllowlist(String.join(",", accessPolicies.ipAllowlist().addresses()));
        } else if (accessPolicies.isPublic()) {
            accessPolicy.setIpAllowlist(null);
        }

        if (accessPolicies.country() != null && accessPolicies.country().countries() != null) {
            accessPolicy.setCountries(String.join(",", accessPolicies.country().countries()));
        } else if (accessPolicies.isPublic()) {
            accessPolicy.setCountries(null);
        }

        if (accessPolicies.device() != null && accessPolicies.device().devices() != null) {
            accessPolicy.setDeviceTypes(String.join(",", accessPolicies.device().devices()));
        } else if (accessPolicies.isPublic()) {
            accessPolicy.setDeviceTypes(null);
        }

        if (accessPolicies.referrer() != null && accessPolicies.referrer().referrers() != null) {
            accessPolicy.setReferrers(String.join(",", accessPolicies.referrer().referrers()));
        } else if (accessPolicies.isPublic()) {
            accessPolicy.setReferrers(null);
        }

        if (accessPolicies.otp() != null && accessPolicies.otp().isEnabled()) {
            accessPolicy.setOtpEnabled(true);
            List<String> emails = accessPolicies.otp().emails() != null
                    ? accessPolicies.otp().emails().stream().map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).distinct().toList()
                    : List.of();
            accessPolicy.setOtpEmails(String.join(",", emails));

            linkRecipientRepository.deleteByShortUrlId(shortUrlId);

            String linkUrl = repository.findById(shortUrlId).map(NewUrl::getNewUrl).orElse("");
            boolean shouldSend = accessPolicies.otp().shouldSendEmails();

            for (String email : emails) {
                String token = UUID.randomUUID().toString().replace("-", "");
                LinkRecipient recipient = new LinkRecipient(shortUrlId, email, token);
                if (shouldSend && !linkUrl.isBlank() && emailService != null) {
                    try {
                        String trackingPixelUrl = this.domain + "/api/track/email-open/" + token;
                        emailService.sendSecuredLinkInvitation(email, linkUrl, trackingPixelUrl);
                        recipient.setEmailSent(true);
                        recipient.setEmailSentAt(Instant.now());
                    } catch (Exception e) {
                        log.warn("Failed to dispatch invitation email to '{}': {}", email, e.getMessage());
                    }
                }
                linkRecipientRepository.save(recipient);
            }
        } else if (accessPolicies.isPublic()) {
            accessPolicy.setOtpEnabled(false);
            accessPolicy.setOtpEmails(null);
            linkRecipientRepository.deleteByShortUrlId(shortUrlId);
        }

        if (accessPolicies.geofence() != null && accessPolicies.geofence().isValid()) {
            try {
                accessPolicy.setGeofence(objectMapper.writeValueAsString(accessPolicies.geofence()));
            } catch (Exception e) {
                log.warn("Failed to serialize geofence policy: {}", e.getMessage());
            }
        } else if (accessPolicies.isPublic()) {
            accessPolicy.setGeofence(null);
        }

        accessPolicyRepository.save(accessPolicy);
    }

    @Transactional(readOnly = true)
    public List<LinkRecipientDto> getLinkRecipients(Long userId, String publicId) {
        String trimmedPublicId = publicId.trim();
        Optional<NewUrl> found = repository.findByPublicIdAndUserId(trimmedPublicId, userId);
        if (found.isEmpty()) {
            found = repository.findByShortCodeAndUserId(trimmedPublicId, userId);
        }
        if (found.isEmpty()) {
            if (repository.findByPublicId(trimmedPublicId).isPresent() || repository.findByShortCode(trimmedPublicId).isPresent()) {
                throw new AccessDeniedException("Access denied: You do not own this link");
            }
            throw new UrlNotFoundException("Link not found: " + publicId);
        }

        NewUrl entity = found.get();
        List<LinkRecipient> recipients = linkRecipientRepository.findByShortUrlIdOrderByIdAsc(entity.getId());
        return recipients.stream().map(r -> {
            String status;
            if (r.isPageOpened()) {
                status = "Page Verified";
            } else if (r.isOtpRequested()) {
                status = "OTP Requested";
            } else if (r.isEmailOpened()) {
                status = "Email Opened";
            } else if (r.isEmailSent()) {
                status = "Email Delivered";
            } else {
                status = "Pending";
            }
            return new LinkRecipientDto(
                    r.getId(),
                    r.getShortUrlId(),
                    r.getEmail(),
                    r.getTrackingToken(),
                    r.isEmailSent(),
                    r.getEmailSentAt(),
                    r.isEmailOpened(),
                    r.getEmailOpenedAt(),
                    r.isOtpRequested(),
                    r.getOtpRequestedAt(),
                    r.isPageOpened(),
                    r.getPageOpenedAt(),
                    status,
                    r.getCreatedAt()
            );
        }).toList();
    }

    public Optional<AccessPolicy> getAccessPolicy(Long shortUrlId) {
        return accessPolicyRepository.findByShortUrlId(shortUrlId);
    }

    public Optional<UsagePolicy> getUsagePolicy(Long shortUrlId) {
        return usagePolicyRepository.findByShortUrlId(shortUrlId);
    }

    @Transactional(readOnly = true)
    public Page<UrlListItemResponse> listUrls(Long userId, Pageable pageable) {
        return listUrls(userId, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<UrlListItemResponse> listUrls(Long userId, String likeUrl, Pageable pageable) {
        if (userId == null) {
            throw new AccessDeniedException("User ID is required to fetch URL list");
        }

        Pageable effectivePageable = (pageable == null || pageable.getSort().isUnsorted())
                ? PageRequest.of(
                        pageable != null ? pageable.getPageNumber() : 0,
                        pageable != null ? pageable.getPageSize() : 10,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                  )
                : pageable;

        Page<NewUrl> page = (likeUrl != null && !likeUrl.isBlank())
                ? repository.findAllByUserIdAndLikeUrl(userId, likeUrl.trim(), effectivePageable)
                : repository.findAllByUserId(userId, effectivePageable);
        if (page.isEmpty()) {
            return page.map(entity -> null);
        }

        List<Long> ids = page.getContent().stream().map(NewUrl::getId).toList();
        Map<Long, UsagePolicy> policyMap = usagePolicyRepository.findAllByShortUrlIdIn(ids).stream()
                .collect(Collectors.toMap(UsagePolicy::getShortUrlId, p -> p, (p1, p2) -> p1));

        Map<Long, List<String>> computedTagsMap = new HashMap<>();
        if (!ids.isEmpty()) {
            try {
                computedTagsMap = tagRepository.findAllByUrlIdIn(ids).stream()
                        .filter(t -> t.getTag() != null && !t.getTag().isBlank())
                        .collect(Collectors.groupingBy(NewUrlTag::getUrlId, Collectors.mapping(NewUrlTag::getTag, Collectors.toList())));
            } catch (Exception e) {
                log.warn("Failed to retrieve tags for shortUrlIds: {}", e.getMessage());
            }
        }
        final Map<Long, List<String>> tagsMap = computedTagsMap;

        Map<Long, LocalDateTime> latestAccessMap = new HashMap<>();
        if (!ids.isEmpty()) {
            try {
                List<Object[]> rows = accessLogRepository.findLatestAccessTimesByShortUrlIdIn(ids);
                for (Object[] row : rows) {
                    if (row != null && row.length >= 2 && row[0] instanceof Long sid && row[1] instanceof LocalDateTime dt) {
                        latestAccessMap.put(sid, dt);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve latest access timestamps for shortUrlIds: {}", e.getMessage());
            }
        }

        Instant now = Instant.now();
        return page.map(entity -> {
            UsagePolicy policy = policyMap.get(entity.getId());
            boolean expiredByTime = (entity.getExpireAt() != null && !now.isBefore(entity.getExpireAt()))
                    || (policy != null && policy.isExpired());
            boolean expiredByUsage = (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit())
                    || (policy != null && policy.isUsageLimitReached());

            boolean isExpired = expiredByTime || expiredByUsage;
            String expiredReason = null;
            if (expiredByTime && expiredByUsage) {
                expiredReason = "TIME, USAGE";
            } else if (expiredByTime) {
                expiredReason = "TIME";
            } else if (expiredByUsage) {
                expiredReason = "USAGE";
            }

            long timesClicked = entity.getClickCount();
            Instant createdAt = entity.getCreatedAt();
            String createdAgo = toTimeAgo(createdAt);

            LocalDateTime latestAccess = latestAccessMap.get(entity.getId());
            Instant lastUsedAt = (latestAccess != null) ? latestAccess.atZone(ZoneId.systemDefault()).toInstant() : null;
            String lastUsedAgo = (lastUsedAt != null) ? toTimeAgo(lastUsedAt) : null;

            List<String> tags = tagsMap.getOrDefault(entity.getId(), List.of());

            return new UrlListItemResponse(
                    entity.getNewUrl(),
                    entity.getOriginalUrl(),
                    entity.isActive(),
                    isExpired,
                    expiredReason,
                    expiredReason,
                    timesClicked,
                    createdAt,
                    createdAgo,
                    lastUsedAt,
                    lastUsedAgo,
                    entity.getPublicId(),
                    tags,
                    entity.getCreatedBy() != null ? entity.getCreatedBy() : "UI"
            );
        });
    }

    @Transactional(readOnly = true)
    public List<String> getUserTags(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return tagRepository.findDistinctTagsByUserId(userId);
    }

    public static String toTimeAgo(Instant instant) {
        if (instant == null) return null;
        Duration duration = Duration.between(instant, Instant.now());
        long seconds = duration.getSeconds();
        if (seconds < 0) return "just now";
        if (seconds < 60) return "just now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        if (days < 7) return days + (days == 1 ? " day ago" : " days ago");
        long weeks = days / 7;
        if (weeks < 4) return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        long months = days / 30;
        if (months < 12) return months + (months == 1 ? " month ago" : " months ago");
        long years = days / 365;
        return years + (years == 1 ? " year ago" : " years ago");
    }

    @Transactional(readOnly = true)
    public Page<LinkAccessLogResponse> listAccessLogs(Long userId, String publicId, Pageable pageable) {
        String trimmedPublicId = publicId.trim();
        Pageable effectivePageable = (pageable == null || pageable.getSort().isUnsorted())
                ? PageRequest.of(
                        pageable != null ? pageable.getPageNumber() : 0,
                        pageable != null ? pageable.getPageSize() : 20,
                        Sort.by(Sort.Order.desc("accessedAt"), Sort.Order.desc("id"))
                  )
                : pageable;

        Optional<NewUrl> found = repository.findByPublicIdAndUserId(trimmedPublicId, userId);

        NewUrl newUrl = found.orElseThrow(() -> {
            if (repository.findByPublicId(trimmedPublicId).isPresent()) {
                return new AccessDeniedException("Access denied to URL access logs");
            }
            return new UrlNotFoundException("URL not found");
        });

        Page<com.preonsurl.apis.link.entity.NewUrlAccessLog> page = accessLogRepository.findByShortUrlId(newUrl.getId(), effectivePageable);

        return page.map(log -> new LinkAccessLogResponse(
                log.getId(),
                log.getShortUrlId(),
                log.getShortCode(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getReferer(),
                log.getAccessedAt()
        ));
    }

    public NewUrl getNewUrlByPublicId(String publicId){
       return repository
                .findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found"));
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void backfillMissingPublicIds() {
        try {
            List<NewUrl> missing = repository.findAllByPublicIdIsNull();
            if (missing != null && !missing.isEmpty()) {
                for (NewUrl url : missing) {
                    url.setPublicId(NewUrl.generatePublicId());
                }
                repository.saveAll(missing);
                log.info("Backfilled {} short URLs with missing publicId", missing.size());
            }
        } catch (Exception e) {
            log.warn("Could not backfill missing publicIds: {}", e.getMessage());
        }
    }
}


