package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.dto.CreateNewUrlRequest;
import com.preonsurl.apis.link.dto.CreateNewUrlResponse;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.entity.NewUrlTag;
import com.preonsurl.apis.link.enums.LinkMode;
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

                List<String> savedTags = saveTags(saved.getId(), userId, request.tags());

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
                    List<String> savedTags = saveTags(saved.getId(), userId, request.tags());

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

                List<String> savedTags = saveTags(saved.getId(), userId, request.tags());

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

            List<String> savedTags = saveTags(saved.getId(), userId, request.tags());

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
                newUrl.getLinkMode()
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
}
