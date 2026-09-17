package com.preonsurl.apis.service;

import com.preonsurl.apis.dto.CreateShortUrlRequest;
import com.preonsurl.apis.dto.CreateShortUrlResponse;
import com.preonsurl.apis.entity.ShortUrl;
import com.preonsurl.apis.entity.ShortUrlAccessLog;
import com.preonsurl.apis.repository.ShortUrlAccessLogRepository;
import com.preonsurl.apis.repository.ShortUrlRepository;
import com.preonsurl.apis.exception.UrlExpiredException;
import com.preonsurl.core.ShortCodePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ShortUrlService {
    private static final Logger log = LoggerFactory.getLogger(ShortUrlService.class);
    private final int DEFAULT_EXPIRE_YEARS = 10;
    private final ShortUrlRepository repository;
    private final ShortUrlAccessLogRepository accessLogRepository;
    private final ShortCodePool codePool;
    private final String domain;

    public ShortUrlService(ShortUrlRepository repository,
                           ShortUrlAccessLogRepository accessLogRepository,
                           ShortCodePool codePool,
                           @Value("${preonsurl.shortener.domain:http://localhost:8081}") String domain) {
        this.repository = repository;
        this.accessLogRepository = accessLogRepository;
        this.codePool = codePool;
        this.domain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
    }

    @Transactional
    public CreateShortUrlResponse createOrGetShortUrl(CreateShortUrlRequest request) {
        String originalUrl = request.url();
        validateUrl(originalUrl);
        Instant expiresAt = ZonedDateTime.now(ZoneOffset.UTC)
                .plusYears(DEFAULT_EXPIRE_YEARS)
                .toInstant();

        if (request.expire() != null && request.expire().enabled()) {
            expiresAt = request.expire().expireAt();
        }

        String normalizedDirType = normalizeDirType(request.dirType());

        // Check if a short URL already exists for the same URL and dirType
        Optional<ShortUrl> existing;
        if (normalizedDirType != null) {
            existing = repository.findFirstByOriginalUrlAndDirType(originalUrl, normalizedDirType);
        } else {
            existing = repository.findFirstByOriginalUrlAndDirTypeIsNull(originalUrl);
        }

        if (existing.isPresent()) {
            ShortUrl found = existing.get();
            return new CreateShortUrlResponse(
                    found.getFullShortUrl(),
                    found.getShortCode(),
                    found.getOriginalUrl(),
                    found.getDirType(),
                    true,
                    found.getExpireAt()
            );
        }

        // Generate a new unique short code
        String code = generateUniqueShortCode();

        // Build full short URL
        String fullShortUrl;
        if (normalizedDirType != null) {
            fullShortUrl = domain + "/" + normalizedDirType + "/" + code;
        } else {
            fullShortUrl = domain + "/" + code;
        }

        // Save to database
        ShortUrl shortUrl = new ShortUrl(code, originalUrl, normalizedDirType, fullShortUrl, expiresAt);
        ShortUrl saved = repository.save(shortUrl);

        return new CreateShortUrlResponse(
                saved.getFullShortUrl(),
                saved.getShortCode(),
                saved.getOriginalUrl(),
                saved.getDirType(),
                false,
                saved.getExpireAt()
        );
    }

    @Transactional
    public Optional<String> resolveAndRecordClick(String dirType, String shortCode, String ipAddress, String userAgent, String referer) {
        String normalizedDirType = normalizeDirType(dirType);
        Optional<ShortUrl> optionalShortUrl;

        if (normalizedDirType != null) {
            optionalShortUrl = repository.findByDirTypeAndShortCode(normalizedDirType, shortCode);
        } else {
            optionalShortUrl = repository.findByShortCode(shortCode);
        }

        if (optionalShortUrl.isPresent()) {
            ShortUrl entity = optionalShortUrl.get();

            if (entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt())) {
                log.warn("Short URL expired: code='{}', dirType='{}', expireAt='{}'", shortCode, normalizedDirType, entity.getExpireAt());
                throw new UrlExpiredException("Short URL has expired");
            }

            repository.incrementClickCount(entity.getId());

            ShortUrlAccessLog accessLog = new ShortUrlAccessLog(
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
