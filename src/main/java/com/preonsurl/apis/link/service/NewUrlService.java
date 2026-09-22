package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.cache.NewUrlLruCache;
import com.preonsurl.apis.link.dto.CreateRequest.CreateNewUrlRequest;
import com.preonsurl.apis.link.dto.CreateNewUrlResponse;
import com.preonsurl.apis.link.dto.EditNewUrlRequest;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.entity.NewUrlChangeLog;
import com.preonsurl.apis.link.entity.NewUrlTag;
import com.preonsurl.apis.link.enums.LinkMode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.preonsurl.apis.link.dto.UrlListItemResponse;

@Service
public class NewUrlService {
    private static final Logger log = LoggerFactory.getLogger(NewUrlService.class);
    private final int DEFAULT_EXPIRE_YEARS = 10;
    private final NewUrlRepository repository;
    private final NewUrlAccessLogRepository accessLogRepository;
    private final NewUrlTagRepository tagRepository;
    private final NewUrlChangeLogRepository changeLogRepository;
    private final NewUrlLruCache lruCache;
    private final ShortCodePool codePool;
    private final String domain;
    private final AccessPolicyRepository accessPolicyRepository;
    private final UsagePolicyRepository usagePolicyRepository;
    private final PasswordEncoder passwordEncoder;

    public NewUrlService(NewUrlRepository repository,
                         NewUrlAccessLogRepository accessLogRepository,
                         NewUrlTagRepository tagRepository,
                         NewUrlChangeLogRepository changeLogRepository,
                         NewUrlLruCache lruCache,
                         ShortCodePool codePool,
                         @Value("${preonsurl.shortener.domain:http://localhost:8081}") String domain,
                         AccessPolicyRepository accessPolicyRepository,
                         UsagePolicyRepository usagePolicyRepository,
                         PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.accessLogRepository = accessLogRepository;
        this.tagRepository = tagRepository;
        this.changeLogRepository = changeLogRepository;
        this.lruCache = lruCache;
        this.codePool = codePool;
        this.domain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        this.accessPolicyRepository = accessPolicyRepository;
        this.usagePolicyRepository = usagePolicyRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @Transactional
    public CreateNewUrlResponse createNewUrl(CreateNewUrlRequest request, Long userId) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
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

        if (customPath != null) {
            if (addShortCode) {
                // Check if an existing URL exists for the same originalUrl and customPath that is still usable
                Optional<NewUrl> existing = repository.findAllByOriginalUrlAndCustomPathOrderByIdDesc(originalUrl, customPath)
                        .stream()
                        .filter(this::isUsable)
                        .findFirst();

                if (existing.isPresent()) {
                    NewUrl found = existing.get();
                    if (found.getUserId() == null && userId != null) {
                        found.setUserId(userId);
                        repository.save(found);
                    }
                    if (found.getNote() == null && request.notes() != null && !request.notes().isBlank()) {
                        found.setNote(request.notes().trim());
                        repository.save(found);
                    }
                    if (found.getLinkMode() != linkMode && request.linkMode() != null) {
                        found.setLinkMode(linkMode);
                        repository.save(found);
                    }
                    List<String> tags = saveTags(found.getId(), userId, request.tags());
                    if (tags.isEmpty()) {
                        tags = tagRepository.findByUrlId(found.getId()).stream().map(NewUrlTag::getTag).toList();
                    }
                    savePolicies(found.getId(), request, found.getExpireAt());
                    return new CreateNewUrlResponse(
                            found.getNewUrl(),
                            found.getOriginalUrl(),
                            customPath,
                            true,
                            found.getExpireAt(),
                            found.getUsageLimit(),
                            found.getNote(),
                            tags,
                            found.getLinkMode()
                    );
                }


                // If no usable existing URL found, generate a new short code and append to customPath
                String shortCode = generateUniqueShortCode();
                String fullPath = customPath + "/" + shortCode;
                String newUrl = domain + "/" + fullPath;

                NewUrl newUrlEntity = new NewUrl(shortCode, originalUrl, customPath, newUrl, expiresAt, request.resolvedUsageLimit(), linkMode);
                newUrlEntity.setUserId(userId);
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
                        saved.getLinkMode()
                );
            } else {
                String fullPath = customPath;
                String shortCode = customPath;
                String newUrl = domain + "/" + fullPath;

                Optional<NewUrl> existingByPath = repository.findByNewUrl(newUrl);
                if (existingByPath.isEmpty()) {
                    existingByPath = repository.findByShortCode(shortCode);
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
                                existing.getLinkMode()
                        );
                    }

                    // Condition exhausted: renew/reactivate the existing custom path entry
                    existing.setActive(true);
                    existing.setExpireAt(expiresAt);
                    existing.setUsageLimit(request.resolvedUsageLimit());
                    existing.setClickCount(0);
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
                            saved.getLinkMode()
                    );
                }

                // Save to database
                NewUrl newUrlEntity = new NewUrl(shortCode, originalUrl, customPath, newUrl, expiresAt, request.resolvedUsageLimit(), linkMode);
                newUrlEntity.setUserId(userId);
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
                        saved.getLinkMode()
                );
            }
        } else {
            // Check if an auto-generated short URL already exists for the same URL that is active, non-expired, and non-limit-exceeded
            Optional<NewUrl> existing = repository.findAllByOriginalUrlAndCustomPathIsNullOrderByIdDesc(originalUrl)
                    .stream()
                    .filter(this::isUsable)
                    .findFirst();

            if (existing.isPresent()) {
                NewUrl found = existing.get();
                if (found.getUserId() == null && userId != null) {
                    found.setUserId(userId);
                    repository.save(found);
                }
                if (found.getNote() == null && request.notes() != null && !request.notes().isBlank()) {
                    found.setNote(request.notes().trim());
                    repository.save(found);
                }
                if (found.getLinkMode() != linkMode && request.linkMode() != null) {
                    found.setLinkMode(linkMode);
                    repository.save(found);
                }
                List<String> tags = saveTags(found.getId(), userId, request.tags());
                if (tags.isEmpty()) {
                    tags = tagRepository.findByUrlId(found.getId()).stream().map(NewUrlTag::getTag).toList();
                }
                savePolicies(found.getId(), request, found.getExpireAt());
                return new CreateNewUrlResponse(
                        found.getNewUrl(),
                        found.getOriginalUrl(),
                        null,
                        true,
                        found.getExpireAt(),
                        found.getUsageLimit(),
                        found.getNote(),
                        tags,
                        found.getLinkMode()
                );
            }

            // If no usable URL exists, generate a new short code
            String code = generateUniqueShortCode();
            String newUrl = domain + "/" + code;

            NewUrl newUrlEntity = new NewUrl(code, originalUrl, null, newUrl, expiresAt, request.resolvedUsageLimit(), linkMode);
            newUrlEntity.setUserId(userId);
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
                    saved.getLinkMode()
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

    @Transactional(readOnly = true)
    public CreateNewUrlResponse getLinkInfo(String urlParam, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("User ID is required to fetch link details");
        }

        if (urlParam == null || urlParam.isBlank()) {
            throw new IllegalArgumentException("URL parameter 'newUrl' cannot be empty");
        }

        String trimmedUrl = urlParam.trim();

        Optional<NewUrl> found = repository.findByNewUrlAndUserId(trimmedUrl, userId);

        if (found.isEmpty() && !trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            String prefixed = domain + (trimmedUrl.startsWith("/") ? "" : "/") + trimmedUrl;
            found = repository.findByNewUrlAndUserId(prefixed, userId);
        }

        if (found.isEmpty()) {
            found = repository.findByShortCodeAndUserId(trimmedUrl, userId);
        }

        NewUrl newUrl = found.orElseThrow(() -> new UrlNotFoundException("URL not found"));

        List<String> tags = tagRepository.findByUrlId(newUrl.getId()).stream()
                .map(NewUrlTag::getTag)
                .toList();

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
                newUrl.isActive()
        );
    }

    @Transactional
    public CreateNewUrlResponse editNewUrl(EditNewUrlRequest request, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("User ID is required to edit link");
        }
        if (request == null || request.newUrl() == null || request.newUrl().isBlank()) {
            throw new IllegalArgumentException("URL parameter 'newUrl' cannot be empty");
        }

        String trimmedUrl = request.newUrl().trim();

        // 1. Locate entity by newUrl or shortCode for this user
        Optional<NewUrl> found = repository.findByNewUrlAndUserId(trimmedUrl, userId);

        if (found.isEmpty() && !trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            String prefixed = domain + (trimmedUrl.startsWith("/") ? "" : "/") + trimmedUrl;
            found = repository.findByNewUrlAndUserId(prefixed, userId);
        }

        if (found.isEmpty()) {
            found = repository.findByShortCodeAndUserId(trimmedUrl, userId);
        }

        NewUrl entity = found.orElseThrow(() -> new UrlNotFoundException("Link not found or access denied"));

        LinkMode finalMode = request.linkMode() != null ? request.linkMode() : entity.getLinkMode();
        String finalUrl = (request.originalUrl() != null && !request.originalUrl().isBlank())
                ? request.originalUrl().trim()
                : entity.getOriginalUrl();
        if (finalMode == LinkMode.PROXY || finalMode == LinkMode.MIRROR) {
            ProxyResourceValidator.validateProxyUrl(finalUrl);
        }

        boolean modified = false;

        // A. originalUrl
        if (request.originalUrl() != null && !request.originalUrl().isBlank()) {
            String newOriginalUrl = request.originalUrl().trim();
            validateUrl(newOriginalUrl);
            if (!Objects.equals(entity.getOriginalUrl(), newOriginalUrl)) {
                String oldVal = entity.getOriginalUrl();
                entity.setOriginalUrl(newOriginalUrl);
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "original_url", oldVal, newOriginalUrl));
                modified = true;
            }
        }

        // B. customPath
        if (request.customPath() != null) {
            String newCustomPath = request.customPath().trim();
            if (newCustomPath.isBlank()) {
                newCustomPath = null;
            }
            if (!Objects.equals(entity.getCustomPath(), newCustomPath)) {
                String oldVal = entity.getCustomPath();
                entity.setCustomPath(newCustomPath);
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "custom_path", oldVal, newCustomPath));
                modified = true;
            }
        }

        // C. expireAt
        if (request.expireAt() != null) {
            if (!Objects.equals(entity.getExpireAt(), request.expireAt())) {
                String oldVal = entity.getExpireAt() != null ? entity.getExpireAt().toString() : null;
                entity.setExpireAt(request.expireAt());
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "expire_at", oldVal, request.expireAt().toString()));
                modified = true;
            }
        }

        // D. usageLimit
        if (request.hasUsageLimit()) {
            Long newUsageLimit = request.resolvedUsageLimit();
            if (!Objects.equals(entity.getUsageLimit(), newUsageLimit)) {
                String oldVal = entity.getUsageLimit() != null ? entity.getUsageLimit().toString() : null;
                String newVal = newUsageLimit != null ? newUsageLimit.toString() : null;
                entity.setUsageLimit(newUsageLimit);
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "usage_limit", oldVal, newVal));
                modified = true;
            }
        }

        if (request.expireAt() != null || request.hasUsageLimit()) {
            usagePolicyRepository.findByShortUrlId(entity.getId()).ifPresent(up -> {
                if (request.expireAt() != null) {
                    up.setExpireAt(request.expireAt());
                }
                if (request.hasUsageLimit()) {
                    up.setUsageLimit(request.resolvedUsageLimit());
                    up.setPolicyType(request.resolvedUsageLimit() != null ? UsagePolicyType.USAGE_LIMIT : UsagePolicyType.UNLIMITED);
                }
                usagePolicyRepository.save(up);
            });
        }


        // E. notes
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

        // F. linkMode
        if (request.linkMode() != null) {
            if (!Objects.equals(entity.getLinkMode(), request.linkMode())) {
                String oldVal = entity.getLinkMode() != null ? entity.getLinkMode().name() : null;
                entity.setLinkMode(request.linkMode());
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "link_mode", oldVal, request.linkMode().name()));
                modified = true;
            }
        }

        // G. isActive
        if (request.isActive() != null) {
            if (entity.isActive() != request.isActive()) {
                String oldVal = String.valueOf(entity.isActive());
                String newVal = String.valueOf(request.isActive());
                entity.setActive(request.isActive());
                changeLogRepository.save(new NewUrlChangeLog(entity.getId(), userId, "EDITED", "is_active", oldVal, newVal));
                modified = true;
            }
        }

        // H. tags
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
            // Invalidate in LRU cache
            lruCache.remove(entity.getNewUrl());
        }

        List<String> finalTags = tagRepository.findByUrlId(entity.getId()).stream()
                .map(NewUrlTag::getTag)
                .toList();

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
                entity.isActive()
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
            throw new IllegalArgumentException("URL cannot be empty");
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

        accessPolicyRepository.save(accessPolicy);
    }

    public Optional<AccessPolicy> getAccessPolicy(Long shortUrlId) {
        return accessPolicyRepository.findByShortUrlId(shortUrlId);
    }

    public Optional<UsagePolicy> getUsagePolicy(Long shortUrlId) {
        return usagePolicyRepository.findByShortUrlId(shortUrlId);
    }

    @Transactional(readOnly = true)
    public Page<UrlListItemResponse> listUrls(Long userId, Pageable pageable) {
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

        Page<NewUrl> page = repository.findAllByUserId(userId, effectivePageable);
        if (page.isEmpty()) {
            return page.map(entity -> null);
        }

        List<Long> ids = page.getContent().stream().map(NewUrl::getId).toList();
        Map<Long, UsagePolicy> policyMap = usagePolicyRepository.findAllByShortUrlIdIn(ids).stream()
                .collect(Collectors.toMap(UsagePolicy::getShortUrlId, p -> p, (p1, p2) -> p1));

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

            return new UrlListItemResponse(
                    entity.getNewUrl(),
                    entity.getOriginalUrl(),
                    entity.isActive(),
                    isExpired,
                    expiredReason
            );
        });
    }
}

