package com.preonsurl.apis.apikey;

import com.preonsurl.apis.apikey.dto.ApiKeyResponse;
import com.preonsurl.apis.apikey.dto.CreateApiKeyResponse;
import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.auth.cache.UserCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final APIKeyCache apiKeyCache;
    private final UserCache userCache;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            UserRepository userRepository,
            APIKeyCache apiKeyCache,
            UserCache userCache) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.apiKeyCache = apiKeyCache;
        this.userCache = userCache;
    }

    /**
     * Authenticates a user by validating the provided raw API key.
     * Uses cache-aside pattern: APIKeyCache -> Database, and UserCache -> Database.
     */
    public AuthenticatedUser authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        // 1. Try fetch from APIKeyCache, on miss check in DB
        String hash = sha256(apiKey);
        ApiKey key = apiKeyCache.get(hash);

        if (key == null) {
            // Cache miss: query database
            key = apiKeyRepository.findByApiKeyHashAndActiveTrue(hash).orElse(null);
            if (key == null) {
                return null;
            }
            apiKeyCache.put(key);
        } else if (!key.isActive()) {
            apiKeyCache.remove(hash);
            return null;
        }

        // 2. Try fetch from UserCache, on miss check in DB
        User user = userCache.get(key.getUserId());
        if (user == null) {
            // Cache miss: query database
            user = userRepository.findById(key.getUserId()).orElse(null);
            if (user == null) {
                return null;
            }
            userCache.put(user);
        }

        // Throttle lastUsedAt updates: only update DB if null or > 60 seconds since last update
        Instant now = Instant.now();
        if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(now.minusSeconds(60))) {
            try {
                key.setLastUsedAt(now);
                apiKeyRepository.save(key);
            } catch (Exception ignored) {
            }
        }

        return new AuthenticatedUser(user.getId(), user.getTenantId(), user.getUsername());
    }

    /**
     * Creates a new API key for the user.
     * Invariant: Strictly at most 1 active API key at any time for a user.
     * Existing active API keys are deactivated and evicted from cache.
     */
    @Transactional
    public CreateApiKeyResponse createApiKey(Long userId, String name) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        // 1. Enforce strictly 1 active key: deactivate and evict any existing active keys for this user
        List<ApiKey> existingActiveKeys = apiKeyRepository.findAllByUserIdAndActiveTrue(userId);
        if (!existingActiveKeys.isEmpty()) {
            for (ApiKey oldKey : existingActiveKeys) {
                oldKey.setActive(false);
                apiKeyCache.remove(oldKey.getApiKeyHash());
            }
            apiKeyRepository.saveAll(existingActiveKeys);
        }

        // 2. Generate cryptographically secure raw API key
        String rawApiKey = generateSecureApiKey();
        String hash = sha256(rawApiKey);
        String keyName = (name != null && !name.isBlank()) ? name.trim() : "Default API Key";

        ApiKey newKey = new ApiKey();
        newKey.setUserId(userId);
        newKey.setApiKeyHash(hash);
        newKey.setName(keyName);
        newKey.setActive(true);

        ApiKey saved = apiKeyRepository.save(newKey);

        // 3. Put into cache
        apiKeyCache.put(saved);

        return new CreateApiKeyResponse(
                saved.getId(),
                saved.getName(),
                rawApiKey,
                saved.isActive(),
                saved.getCreatedAt()
        );
    }

    /**
     * Deactivates all currently active API keys for the user and evicts them from cache.
     */
    @Transactional
    public boolean deleteActiveApiKey(Long userId) {
        if (userId == null) {
            return false;
        }

        List<ApiKey> activeKeys = apiKeyRepository.findAllByUserIdAndActiveTrue(userId);
        if (activeKeys.isEmpty()) {
            return false;
        }

        for (ApiKey key : activeKeys) {
            key.setActive(false);
            apiKeyCache.remove(key.getApiKeyHash());
        }
        apiKeyRepository.saveAll(activeKeys);
        return true;
    }

    /**
     * Deactivates a specific API key by ID if owned by the user and evicts from cache.
     */
    @Transactional
    public boolean deleteApiKeyById(Long id, Long userId) {
        if (id == null || userId == null) {
            return false;
        }

        return apiKeyRepository.findByIdAndUserId(id, userId)
                .map(key -> {
                    key.setActive(false);
                    apiKeyRepository.save(key);
                    apiKeyCache.remove(key.getApiKeyHash());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Retrieves the currently active API key metadata for a user.
     */
    @Transactional(readOnly = true)
    public ApiKeyResponse getActiveApiKey(Long userId) {
        if (userId == null) {
            return null;
        }

        return apiKeyRepository.findByUserIdAndActiveTrue(userId)
                .map(k -> new ApiKeyResponse(
                        k.getId(),
                        k.getName(),
                        k.isActive(),
                        k.getCreatedAt(),
                        k.getLastUsedAt()
                ))
                .orElse(null);
    }

    public APIKeyCache getApiKeyCache() {
        return apiKeyCache;
    }

    public UserCache getUserCache() {
        return userCache;
    }

    private String generateSecureApiKey() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return "pk_" + HexFormat.of().formatHex(randomBytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}