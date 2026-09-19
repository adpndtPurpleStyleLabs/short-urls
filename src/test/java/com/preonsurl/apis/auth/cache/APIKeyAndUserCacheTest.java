package com.preonsurl.apis.auth.cache;

import com.preonsurl.apis.apikey.APIKeyCache;
import com.preonsurl.apis.apikey.ApiKeyService;
import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.apikey.ApiKey;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.apikey.ApiKeyRepository;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class APIKeyAndUserCacheTest {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private APIKeyCache apiKeyCache;

    @Autowired
    private UserCache userCache;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        apiKeyCache.clear();
        userCache.clear();
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void apiKeyCacheBasicOperations() {
        APIKeyCache cache = new APIKeyCache(2);
        ApiKey k1 = new ApiKey();
        k1.setApiKeyHash("hash1");
        ApiKey k2 = new ApiKey();
        k2.setApiKeyHash("hash2");
        ApiKey k3 = new ApiKey();
        k3.setApiKeyHash("hash3");

        cache.put(k1);
        cache.put(k2);
        assertEquals(2, cache.size());
        assertTrue(cache.containsKey("hash1"));
        assertEquals("hash1", cache.get("hash1").getApiKeyHash());

        // Bounded capacity eviction
        cache.put(k3);
        assertEquals(2, cache.size());
        assertNotNull(cache.get("hash3"));

        cache.remove("hash3");
        assertNull(cache.get("hash3"));
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void userCacheBasicOperations() {
        UserCache cache = new UserCache(2);
        User u1 = new User(1L, "User 1", "u1", "hash1");
        u1.setId(101L);
        User u2 = new User(1L, "User 2", "u2", "hash2");
        u2.setId(102L);
        User u3 = new User(1L, "User 3", "u3", "hash3");
        u3.setId(103L);

        cache.put(u1);
        cache.put(u2);
        assertEquals(2, cache.size());
        assertTrue(cache.containsKey(101L));
        assertEquals("u1", cache.get(101L).getUsername());

        // Bounded capacity eviction
        cache.put(u3);
        assertEquals(2, cache.size());
        assertNotNull(cache.get(103L));

        cache.remove(103L);
        assertNull(cache.get(103L));
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void authenticatePopulatesCacheOnMissAndServesFromCacheOnHit() {
        Tenant tenant = tenantRepository.save(new Tenant("Cache Corp"));
        User user = new User(tenant.getId(), "Grace Hopper", "grace", "passhash");
        user = userRepository.save(user);

        String rawApiKey = "preons_fast_key_123";
        String hash = sha256(rawApiKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(user.getId());
        apiKey.setApiKeyHash(hash);
        apiKey.setName("Cache Test Key");
        apiKey.setActive(true);
        apiKey = apiKeyRepository.save(apiKey);

        // Before authentication: caches are empty
        assertEquals(0, apiKeyCache.size());
        assertEquals(0, userCache.size());

        // 1st Authenticate: Cache miss -> loads from DB -> populates both caches
        AuthenticatedUser authUser = apiKeyService.authenticate(rawApiKey);
        assertNotNull(authUser);
        assertEquals("grace", authUser.username());
        assertEquals(user.getId(), authUser.userId());

        assertEquals(1, apiKeyCache.size());
        assertEquals(1, userCache.size());
        assertTrue(apiKeyCache.containsKey(hash));
        assertTrue(userCache.containsKey(user.getId()));

        // Delete records from database to prove second call is served directly from cache!
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
        assertEquals(0, apiKeyRepository.count());
        assertEquals(0, userRepository.count());

        // 2nd Authenticate: Cache hit -> succeeds even though DB records were deleted!
        AuthenticatedUser cachedAuthUser = apiKeyService.authenticate(rawApiKey);
        assertNotNull(cachedAuthUser);
        assertEquals("grace", cachedAuthUser.username());
        assertEquals(user.getId(), cachedAuthUser.userId());
    }

    @Test
    void inactiveApiKeyInCacheIsEvictedAndFails() {
        Tenant tenant = tenantRepository.save(new Tenant("Inactive Corp"));
        User user = userRepository.save(new User(tenant.getId(), "Inactive User", "inactive_user", "passhash"));

        String rawApiKey = "preons_inactive_key";
        String hash = sha256(rawApiKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(user.getId());
        apiKey.setApiKeyHash(hash);
        apiKey.setName("Inactive Key");
        apiKey.setActive(false); // Inactive!
        apiKeyRepository.save(apiKey);

        AuthenticatedUser authUser = apiKeyService.authenticate(rawApiKey);
        assertNull(authUser);
        assertNull(apiKeyCache.get(hash));
    }
}
