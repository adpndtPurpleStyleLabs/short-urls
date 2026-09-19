package com.preonsurl.apis;

import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.apikey.APIKeyCache;
import com.preonsurl.apis.apikey.ApiKey;
import com.preonsurl.apis.apikey.ApiKeyRepository;
import com.preonsurl.apis.auth.cache.UserCache;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.link.entity.NewUrlTag;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.repository.NewUrlTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CreateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewUrlRepository shortUrlRepository;

    @Autowired
    private NewUrlTagRepository tagRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private APIKeyCache apiKeyCache;

    @Autowired
    private UserCache userCache;

    private static final String VALID_API_KEY = "test-api-key-12345";
    private static final String INVALID_API_KEY = "wrong-api-key";

    @BeforeEach
    void setUp() {
        apiKeyCache.clear();
        userCache.clear();
        tagRepository.deleteAll();
        shortUrlRepository.deleteAll();
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = tenantRepository.save(new Tenant("Test Tenant"));
        User user = userRepository.save(new User(tenant.getId(), "Test User", "testuser", "hashedpass"));

        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(user.getId());
        apiKey.setName("Default Test Key");
        apiKey.setApiKeyHash(sha256(VALID_API_KEY));
        apiKey.setActive(true);
        apiKeyRepository.save(apiKey);
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
    void createWithoutApiKeyReturns401Unauthorized() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/test"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void createWithInvalidApiKeyReturns401Unauthorized() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/test"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", INVALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void createWithValidApiKeySucceedsAndGeneratesShortUrl() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/products/item1"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Short URL created successfully"))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/products/item1"))
                .andExpect(jsonPath("$.data.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.data.shortUrl", startsWith("http://localhost:8081/")))
                .andExpect(jsonPath("$.data.existing").value(false))
                .andExpect(jsonPath("$.data.expireAt").isNotEmpty());

        assertEquals(1, shortUrlRepository.count());
    }

    @Test
    void createWithDirTypeIncludesDirInShortUrl() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/invoices/999",
                    "dirType": "/invoice"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/invoices/999"))
                .andExpect(jsonPath("$.data.dirType").value("invoice"))
                .andExpect(jsonPath("$.data.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.data.shortUrl", startsWith("http://localhost:8081/invoice/")))
                .andExpect(jsonPath("$.data.existing").value(false))
                .andExpect(jsonPath("$.data.expireAt").isNotEmpty());

        assertEquals(1, shortUrlRepository.count());
    }

    @Test
    void createForSameUrlReturnsExistingShortCodeWithoutDuplicate() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/duplicate-check",
                    "dirType": "/invoice"
                }
                """;

        // First creation
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.existing").value(false));

        // Second creation for same URL and dirType
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.existing").value(true));

        assertEquals(1, shortUrlRepository.count(), "Should not create a duplicate row in DB");
    }

    @Test
    void createWithBearerAuthHeaderSucceeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/bearer-test"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("Authorization", "Bearer " + VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.existing").value(false));
    }

    @Test
    void createWithInvalidUrlReturns400BadRequest() throws Exception {
        String payload = """
                {
                    "url": "ftp://invalid-url.com"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Only http:// and https:// URLs are supported"));
    }

    @Test
    void createWithEmptyUrlReturns400BadRequest() throws Exception {
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithCustomExpireAtSucceeds() throws Exception {
        Instant customExpire = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String payload = """
                {
                    "url": "https://example.com/custom-expire",
                    "expire": {
                        "enabled": true,
                        "expireAt": "%s"
                    }
                }
                """.formatted(customExpire.toString());

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.expireAt", notNullValue()));

        assertEquals(1, shortUrlRepository.count());
    }

    @Test
    void createWithExpireAtInPastReturns400BadRequest() throws Exception {
        Instant pastExpire = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String payload = """
                {
                    "url": "https://example.com/past-expire",
                    "expire": {
                        "enabled": true,
                        "expireAt": "%s"
                    }
                }
                """.formatted(pastExpire.toString());

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("expireAt must be greater than the current UTC time")));
    }

    @Test
    void createWithExpireEnabledAndNullExpireAtReturns400BadRequest() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/null-expire-at",
                    "expire": {
                        "enabled": true,
                        "expireAt": null
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("expireAt is required when expiration is enabled")));
    }

    @Test
    void createWithExpireDisabledAndNonNullExpireAtReturns400BadRequest() throws Exception {
        Instant futureExpire = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String payload = """
                {
                    "url": "https://example.com/disabled-expire-with-date",
                    "expire": {
                        "enabled": false,
                        "expireAt": "%s"
                    }
                }
                """.formatted(futureExpire.toString());

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("expireAt must be null when expiration is disabled")));
    }

    @Test
    void createWithUsageLimitOnceSucceeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/once-test",
                    "usageLimit": "once"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usageLimit").value(1));

        assertEquals(1, shortUrlRepository.count());
    }

    @Test
    void createWithUsageLimitUnlimitedSucceeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/unlimited-test",
                    "usageLimit": "unlimited"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usageLimit").doesNotExist());

        assertEquals(1, shortUrlRepository.count());
    }

    @Test
    void createWithUsageLimitNumberSucceeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/number-limit-test",
                    "usageLimit": 5
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usageLimit").value(5));

        assertEquals(1, shortUrlRepository.count());
    }

    @Test
    void createWithUsageLimitNullSucceeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/null-limit-test",
                    "usageLimit": null
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usageLimit").doesNotExist());

        assertEquals(1, shortUrlRepository.count());
    }

    @Test
    void createWithInvalidUsageLimitReturns400BadRequest() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/invalid-limit-test",
                    "usageLimit": "invalid_val"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createWithCustomSlugUsesSlugAsShortCodeWithoutGeneratingNewCode() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/diwali-offer",
                    "slug": {
                        "value": "diwali-sale"
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").value("diwali-sale"))
                .andExpect(jsonPath("$.data.shortUrl").value("http://localhost:8081/diwali-sale"))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/diwali-offer"))
                .andExpect(jsonPath("$.data.existing").value(false));

        assertEquals(1, shortUrlRepository.count());
        assertTrue(shortUrlRepository.findByShortCode("diwali-sale").isPresent());
    }

    @Test
    void createWithHierarchicalSlugSucceeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/summer-collection",
                    "slug": {
                        "value": "promo/summer_deals-2026"
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").value("promo/summer_deals-2026"))
                .andExpect(jsonPath("$.data.shortUrl").value("http://localhost:8081/promo/summer_deals-2026"));
    }

    @Test
    void createWithDirTypeAndCustomSlugSucceeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/special-deal",
                    "dirType": "deals",
                    "slug": {
                        "value": "flash-sale"
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dirType").value("deals"))
                .andExpect(jsonPath("$.data.shortCode").value("flash-sale"))
                .andExpect(jsonPath("$.data.shortUrl").value("http://localhost:8081/deals/flash-sale"));
    }

    @Test
    void createWithDuplicateSlugForSameUrlReturnsExisting() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/reused-url",
                    "slug": {
                        "value": "unique-tag-1"
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(false));

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(true))
                .andExpect(jsonPath("$.data.shortCode").value("unique-tag-1"));
    }

    @Test
    void createWithDuplicateSlugForDifferentUrlReturns400BadRequest() throws Exception {
        String payload1 = """
                {
                    "url": "https://example.com/first-owner",
                    "slug": {
                        "value": "claimed-slug"
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isOk());

        String payload2 = """
                {
                    "url": "https://example.com/second-owner",
                    "slug": {
                        "value": "claimed-slug"
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("already in use")));
    }

    @Test
    void createWithInvalidSlugPatternsReturns400BadRequest() throws Exception {
        String[] invalidSlugs = {
                "invalid@char",
                "/leading-slash",
                "trailing-slash/",
                "double//slash",
                "space in slug",
                "question?mark",
                "hash#tag"
        };

        for (String invalidSlug : invalidSlugs) {
            String payload = """
                    {
                        "url": "https://example.com/invalid-test",
                        "slug": {
                            "value": "%s"
                        }
                    }
                    """.formatted(invalidSlug);

            mockMvc.perform(post("/link/create")
                            .header("X-API-KEY", VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Invalid slug format")));
        }
    }

    @Test
    void createWithEmptySlugReturns400BadRequest() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/empty-slug",
                    "slug": {
                        "value": "   "
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("cannot be empty")));
    }

    @Test
    void createWithNotesAndTagsSucceedsAndPersistsToDatabase() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/tagged-link",
                    "notes": "Important marketing campaign link",
                    "tags": ["marketing", "q3-promo", "sale"]
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.notes").value("Important marketing campaign link"))
                .andExpect(jsonPath("$.data.tags", containsInAnyOrder("marketing", "q3-promo", "sale")));

        NewUrl newUrl = shortUrlRepository.findFirstByOriginalUrlAndDirTypeIsNull("https://example.com/tagged-link").orElseThrow();
        assertEquals("Important marketing campaign link", newUrl.getNote());

        List<NewUrlTag> savedTags = tagRepository.findByUrlId(newUrl.getId());
        assertEquals(3, savedTags.size());
        assertTrue(savedTags.stream().anyMatch(t -> t.getTag().equals("marketing")));
        assertTrue(savedTags.stream().anyMatch(t -> t.getTag().equals("q3-promo")));
        assertTrue(savedTags.stream().anyMatch(t -> t.getTag().equals("sale")));
        assertNotNull(savedTags.get(0).getUserId(), "User ID should be associated from authenticated user");
    }

    @Test
    void createWithDuplicateTagsDeduplicatesTags() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/dedup-tag-test",
                    "tags": ["tech", "tech", " AI ", "tech"]
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tags", hasSize(2)))
                .andExpect(jsonPath("$.data.tags", containsInAnyOrder("tech", "AI")));

        NewUrl newUrl = shortUrlRepository.findFirstByOriginalUrlAndDirTypeIsNull("https://example.com/dedup-tag-test").orElseThrow();
        List<NewUrlTag> savedTags = tagRepository.findByUrlId(newUrl.getId());
        assertEquals(2, savedTags.size());
    }

    @Test
    void createWithNoteAliasSucceeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/note-alias-test",
                    "note": "Single note alias test"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.notes").value("Single note alias test"));

        NewUrl newUrl = shortUrlRepository.findFirstByOriginalUrlAndDirTypeIsNull("https://example.com/note-alias-test").orElseThrow();
        assertEquals("Single note alias test", newUrl.getNote());
    }

    @Test
    void getLinkInfo_success_returnsCreateShortUrlResponse() throws Exception {
        String createPayload = """
                {
                    "url": "https://example.com/details-test",
                    "dirType": "promo",
                    "notes": "Details test note",
                    "tags": ["details", "promo"]
                }
                """;

        String createRes = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String shortUrl = createRes.split("\"shortUrl\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("fullUrl", shortUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/details-test"))
                .andExpect(jsonPath("$.data.dirType").value("promo"))
                .andExpect(jsonPath("$.data.notes").value("Details test note"))
                .andExpect(jsonPath("$.data.tags", containsInAnyOrder("details", "promo")))
                .andExpect(jsonPath("$.data.shortUrl").value(shortUrl))
                .andExpect(jsonPath("$.data.shortCode", notNullValue()));
    }


    @Test
    void getLinkInfo_byFullShortUrl_success() throws Exception {
        String createPayload = """
                {
                    "url": "https://example.com/short-code-lookup-test"
                }
                """;

        String createRes = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Extract shortCode and newUrl
        String shortCode = createRes.split("\"shortCode\":\"")[1].split("\"")[0];
        String shortUrl = createRes.split("\"shortUrl\":\"")[1].split("\"")[0];

        // Lookup by full newUrl via fullUrl
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("fullUrl", shortUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortCode").value(shortCode))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/short-code-lookup-test"));
    }

    @Test
    void getLinkInfo_missingOrEmptyUrl_returns400BadRequest() throws Exception {
        // Missing param
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("cannot be empty")));

        // Empty param
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("fullUrl", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("cannot be empty")));
    }

    @Test
    void getLinkInfo_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/link")
                        .param("fullUrl", "https://example.com/unauth-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getLinkInfo_nonExistentUrl_returns404NotFound() throws Exception {
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("fullUrl", "https://nonexistent-404-link.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    void getLinkInfo_otherUsersLink_returns404NotFound() throws Exception {
        // User 1 creates link
        String createPayload = """
                {
                    "url": "https://example.com/secret-user1-page",
                    "notes": "User 1 confidential note"
                }
                """;

        String createRes = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String shortUrl = createRes.split("\"shortUrl\":\"")[1].split("\"")[0];

        // Create User 2 with User 2's API key
        Tenant tenant2 = tenantRepository.save(new Tenant("Second Tenant"));
        User user2 = userRepository.save(new User(tenant2.getId(), "Second User", "seconduser", "pass123"));
        String USER2_API_KEY = "user2-secret-key-99999";

        ApiKey apiKey2 = new ApiKey();
        apiKey2.setUserId(user2.getId());
        apiKey2.setName("User2 Key");
        apiKey2.setApiKeyHash(sha256(USER2_API_KEY));
        apiKey2.setActive(true);
        apiKeyRepository.save(apiKey2);

        // User 2 attempts to fetch User 1's link -> not found for User 2
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", USER2_API_KEY)
                        .param("fullUrl", shortUrl))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("not found")));
    }
}
