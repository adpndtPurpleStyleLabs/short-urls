package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.dto.CreateNewUrlRequest;
import com.preonsurl.apis.link.dto.CreateNewUrlResponse;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.entity.NewUrlTag;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.repository.NewUrlTagRepository;
import com.preonsurl.apis.link.exception.UrlExpiredException;
import com.preonsurl.apis.link.exception.UrlNotFoundException;
import com.preonsurl.apis.link.exception.UrlUsageLimitExceededException;
import com.preonsurl.core.ShortCodePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class NewUrlService {
    private static final Logger log = LoggerFactory.getLogger(NewUrlService.class);
    private final int DEFAULT_EXPIRE_YEARS = 10;
    private final NewUrlRepository repository;
    private final NewUrlAccessLogRepository accessLogRepository;
    private final NewUrlTagRepository tagRepository;
    private final ShortCodePool codePool;
    private final String domain;

    public NewUrlService(NewUrlRepository repository,
                         NewUrlAccessLogRepository accessLogRepository,
                         NewUrlTagRepository tagRepository,
                         ShortCodePool codePool,
                         @Value("${preonsurl.shortener.domain:http://localhost:8081}") String domain) {
        this.repository = repository;
        this.accessLogRepository = accessLogRepository;
        this.tagRepository = tagRepository;
        this.codePool = codePool;
        this.domain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
    }

    @Transactional
    public CreateNewUrlResponse createNewUrl(CreateNewUrlRequest request, Long userId) {

        String originalUrl = request.url();
        validateUrl(originalUrl);

        if (request.expire() != null) {
            request.expire().validate();
        }

        Instant expiresAt = ZonedDateTime.now(ZoneOffset.UTC)
                .plusYears(DEFAULT_EXPIRE_YEARS)
                .toInstant();

        if (request.expire() != null && request.expire().enabled()) {
            expiresAt = request.expire().expireAt();
        }

        String normalizedDirType = normalizeDirType(request.dirType());
        String customSlug = request.resolvedSlug();

        if (customSlug != null) {
            Optional<NewUrl> existingBySlug = repository.findByShortCode(customSlug);
            if (existingBySlug.isPresent()) {
                NewUrl existing = existingBySlug.get();
                if (existing.getOriginalUrl().equals(originalUrl) &&
                        java.util.Objects.equals(existing.getDirType(), normalizedDirType)) {
                    if (existing.getUserId() == null && userId != null) {
                        existing.setUserId(userId);
                        repository.save(existing);
                    }
                    if (existing.getNote() == null && request.notes() != null && !request.notes().isBlank()) {
                        existing.setNote(request.notes().trim());
                        repository.save(existing);
                    }
                    List<String> tags = saveTags(existing.getId(), userId, request.tags());
                    if (tags.isEmpty()) {
                        tags = tagRepository.findByUrlId(existing.getId()).stream().map(NewUrlTag::getTag).toList();
                    }
                    return new CreateNewUrlResponse(
                            existing.getFullShortUrl(),
                            existing.getOriginalUrl(),
                            existing.getDirType(),
                            true,
                            existing.getExpireAt(),
                            existing.getUsageLimit(),
                            existing.getNote(),
                            tags
                    );
                }
                throw new IllegalArgumentException("Slug '" + customSlug + "' is already in use");
            }
        } else {
            // Check if a short URL already exists for the same URL and dirType
            Optional<NewUrl> existing;
            if (normalizedDirType != null) {
                existing = repository.findFirstByOriginalUrlAndDirType(originalUrl, normalizedDirType);
            } else {
                existing = repository.findFirstByOriginalUrlAndDirTypeIsNull(originalUrl);
            }

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
                List<String> tags = saveTags(found.getId(), userId, request.tags());
                if (tags.isEmpty()) {
                    tags = tagRepository.findByUrlId(found.getId()).stream().map(NewUrlTag::getTag).toList();
                }
                return new CreateNewUrlResponse(
                        found.getFullShortUrl(),
                        found.getOriginalUrl(),
                        found.getDirType(),
                        true,
                        found.getExpireAt(),
                        found.getUsageLimit(),
                        found.getNote(),
                        tags
                );
            }
        }

        // If slug is present, use slug as short code without generating a new code
        String code = (customSlug != null) ? customSlug : generateUniqueShortCode();

        // Build full short URL
        String fullShortUrl;
        if (normalizedDirType != null) {
            fullShortUrl = domain + "/" + normalizedDirType + "/" + code;
        } else {
            fullShortUrl = domain + "/" + code;
        }

        // Save to database
        NewUrl newUrl = new NewUrl(code, originalUrl, normalizedDirType, fullShortUrl, expiresAt, request.resolvedUsageLimit());
        newUrl.setUserId(userId);
        if (request.notes() != null && !request.notes().isBlank()) {
            newUrl.setNote(request.notes().trim());
        }
        NewUrl saved = repository.save(newUrl);

        List<String> savedTags = saveTags(saved.getId(), userId, request.tags());

        return new CreateNewUrlResponse(
                saved.getFullShortUrl(),
                saved.getOriginalUrl(),
                saved.getDirType(),
                false,
                saved.getExpireAt(),
                saved.getUsageLimit(),
                saved.getNote(),
                savedTags
        );
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
    public CreateNewUrlResponse getLinkInfo(String fullUrl, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("User ID is required to fetch link details");
        }

        if (fullUrl == null || fullUrl.isBlank()) {
            throw new IllegalArgumentException("URL parameter 'fullUrl' cannot be empty");
        }

        String trimmedUrl = fullUrl.trim();

        NewUrl newUrl = repository.findByFullShortUrlAndUserId(trimmedUrl, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        List<String> tags = tagRepository.findByUrlId(newUrl.getId()).stream()
                .map(NewUrlTag::getTag)
                .toList();

        return new CreateNewUrlResponse(
                newUrl.getFullShortUrl(),
                newUrl.getOriginalUrl(),
                newUrl.getDirType(),
                true,
                newUrl.getExpireAt(),
                newUrl.getUsageLimit(),
                newUrl.getNote(),
                tags
        );
    }

    @Transactional
    public Optional<String> resolveAndRecordClick(String dirType, String shortCode, String ipAddress, String userAgent, String referer) {
        String normalizedDirType = normalizeDirType(dirType);
        Optional<NewUrl> optionalShortUrl;

        if (normalizedDirType != null) {
            optionalShortUrl = repository.findByDirTypeAndShortCode(normalizedDirType, shortCode);
        } else {
            optionalShortUrl = repository.findByShortCode(shortCode);
        }

        if (optionalShortUrl.isPresent()) {
            NewUrl entity = optionalShortUrl.get();

            if (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt())) {
                log.warn("Short URL expired: code='{}', dirType='{}', expireAt='{}'", shortCode, normalizedDirType, entity.getExpireAt());
                throw new UrlExpiredException("Short URL has expired");
            }

            if (entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit()) {
                log.warn("Short URL usage limit reached: code='{}', dirType='{}', clickCount='{}', usageLimit='{}'",
                        shortCode, normalizedDirType, entity.getClickCount(), entity.getUsageLimit());
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
    public Optional<String> resolveAndRecordClick(String dirType, String shortCode) {
        return resolveAndRecordClick(dirType, shortCode, null, null, null);
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

    public String normalizeDirType(String dirType) {
        if (dirType == null) {
            return null;
        }
        String trimmed = dirType.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // Strip leading slashes
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        // Strip trailing slashes
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
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
}
