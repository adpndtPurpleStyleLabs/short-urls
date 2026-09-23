package com.preonsurl.apis;

import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.apikey.APIKeyCache;
import com.preonsurl.apis.apikey.ApiKey;
import com.preonsurl.apis.apikey.ApiKeyRepository;
import com.preonsurl.apis.auth.cache.UserCache;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.link.entity.NewUrlChangeLog;
import com.preonsurl.apis.link.entity.NewUrlTag;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.repository.NewUrlChangeLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.repository.NewUrlTagRepository;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.domain.entity.CustomDomain;
import com.preonsurl.apis.domain.entity.DomainStatus;
import com.preonsurl.apis.domain.repository.CustomDomainRepository;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private NewUrlChangeLogRepository changeLogRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private NewUrlAccessLogRepository accessLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private APIKeyCache apiKeyCache;

    @Autowired
    private UserCache userCache;

    @Autowired
    private CustomDomainRepository customDomainRepository;

    private static final String VALID_API_KEY = "test-api-key-12345";
    private static final String INVALID_API_KEY = "wrong-api-key";

    @BeforeEach
    void setUp() {
        apiKeyCache.clear();
        userCache.clear();
        changeLogRepository.deleteAll();
        tagRepository.deleteAll();
        accessLogRepository.deleteAll();
        shortUrlRepository.deleteAll();
        customDomainRepository.deleteAll();
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
                .andExpect(jsonPath("$.message").value("New URL created successfully"))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/products/item1"))
                .andExpect(jsonPath("$.data.newUrl", startsWith("http://localhost:8081/")))
                .andExpect(jsonPath("$.data.existing").value(false))
                .andExpect(jsonPath("$.data.expireAt").isNotEmpty());

        assertEquals(1, shortUrlRepository.count());
    }

    @Test
    void createWithDirTypeIncludesDirInShortUrl() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/invoices/999",
                    "customPath": "invoice/999"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/invoices/999"))
                .andExpect(jsonPath("$.data.customPath").value("invoice/999"))
                .andExpect(jsonPath("$.data.linkMode").value("REDIRECT"))
                .andExpect(jsonPath("$.data.newUrl").value("http://localhost:8081/invoice/999"))
                .andExpect(jsonPath("$.data.existing").value(false))
                .andExpect(jsonPath("$.data.expireAt").isNotEmpty());

        assertEquals(1, shortUrlRepository.count());
    }

    @Test
    void createForSameUrlReturnsExistingShortCodeWithoutDuplicate() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/duplicate-check",
                    "customPath": "invoice/dup"
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

        // Second creation for same URL and customPath
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
                    "expireAt": "%s"
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
                    "expireAt": "%s"
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
    void createWithExpireAtNullSucceedsWithDefaultExpiration() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/null-expire-at",
                    "expireAt": null
                }
                """;

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
                    "slug": "diwali-sale"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newUrl").value("http://localhost:8081/diwali-sale"))
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
                    "slug": "promo/summer_deals-2026"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newUrl").value("http://localhost:8081/promo/summer_deals-2026"));
    }

    @Test
    void createWithDirTypeAndCustomSlugSucceeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/special-deal",
                    "customPath": "deals/flash-sale"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.linkMode").value("REDIRECT"))
                .andExpect(jsonPath("$.data.customPath").value("deals/flash-sale"))
                .andExpect(jsonPath("$.data.newUrl").value("http://localhost:8081/deals/flash-sale"));
    }

    @Test
    void createWithDuplicateSlugForSameUrlReturnsExisting() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/reused-url",
                    "slug": "unique-tag-1"
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
                .andExpect(jsonPath("$.data.existing").value(true));
    }

    @Test
    void createWithDuplicateSlugForDifferentUrlReturns400BadRequest() throws Exception {
        String payload1 = """
                {
                    "url": "https://example.com/first-owner",
                    "slug": "claimed-slug"
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
                    "slug": "claimed-slug"
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
                "double//slash",
                "space in slug",
                "question?mark",
                "hash#tag"
        };

        for (String invalidSlug : invalidSlugs) {
            String payload = """
                    {
                        "url": "https://example.com/invalid-test",
                        "customPath": "%s"
                    }
                    """.formatted(invalidSlug);

            mockMvc.perform(post("/link/create")
                            .header("X-API-KEY", VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Invalid customPath format")));
        }
    }

    @Test
    void createWithEmptySlugReturns400BadRequest() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/empty-slug",
                    "customPath": "   "
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

        NewUrl newUrl = shortUrlRepository.findFirstByOriginalUrl("https://example.com/tagged-link").orElseThrow();
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

        NewUrl newUrl = shortUrlRepository.findFirstByOriginalUrl("https://example.com/dedup-tag-test").orElseThrow();
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

        NewUrl newUrl = shortUrlRepository.findFirstByOriginalUrl("https://example.com/note-alias-test").orElseThrow();
        assertEquals("Single note alias test", newUrl.getNote());
    }

    @Test
    void getLinkInfo_success_returnsCreateShortUrlResponse() throws Exception {
        String createPayload = """
                {
                    "url": "https://example.com/details-test",
                    "customPath": "promo/details-test",
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

        String newUrl = createRes.split("\"newUrl\":\"")[1].split("\"")[0];
        String publicId = createRes.split("\"publicId\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("id", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicId").value(publicId))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/details-test"))
                .andExpect(jsonPath("$.data.linkMode").value("REDIRECT"))
                .andExpect(jsonPath("$.data.customPath").value("promo/details-test"))
                .andExpect(jsonPath("$.data.notes").value("Details test note"))
                .andExpect(jsonPath("$.data.tags", containsInAnyOrder("details", "promo")))
                .andExpect(jsonPath("$.data.newUrl").value(newUrl));
    }

    @Test
    void createWithCustomLinkModeSavesAndReturnsLinkMode() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/iframe-test",
                    "customPath": "iframe-page",
                    "linkMode": "IFRAME"
                }
                """;

        String res = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.linkMode").value("IFRAME"))
                .andReturn().getResponse().getContentAsString();

        String newUrl = res.split("\"newUrl\":\"")[1].split("\"")[0];

        NewUrl dbUrl = shortUrlRepository.findByShortCode("iframe-page").orElseThrow();
        assertEquals(LinkMode.IFRAME, dbUrl.getLinkMode());

        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("id", dbUrl.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkMode").value("IFRAME"));
    }

    @Test
    void createWithMirrorLinkModeSavesAndReturnsMirrorMode() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/mirror-test",
                    "customPath": "mirror-page",
                    "linkMode": "MIRROR"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.linkMode").value("MIRROR"));

        NewUrl dbUrl = shortUrlRepository.findByShortCode("mirror-page").orElseThrow();
        assertEquals(LinkMode.MIRROR, dbUrl.getLinkMode());
    }

    @Test
    void createWithMirrorLinkModeRejectsSSRF() throws Exception {
        String payload = """
                {
                    "url": "http://127.0.0.1:8080/admin",
                    "customPath": "ssrf-mirror",
                    "linkMode": "MIRROR"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }


    @Test
    void getLinkInfo_byPublicId_success() throws Exception {
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

        String newUrl = createRes.split("\"newUrl\":\"")[1].split("\"")[0];
        String publicId = createRes.split("\"publicId\":\"")[1].split("\"")[0];

        // Lookup by publicId via id param
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("id", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId").value(publicId))
                .andExpect(jsonPath("$.data.newUrl").value(newUrl))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/short-code-lookup-test"));
    }

    @Test
    void getLinkInfo_missingOrEmptyUrl_returns400BadRequest() throws Exception {
        // Missing param: Spring returns 400 Bad Request
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY))
                .andExpect(status().isBadRequest());

        // Empty param
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("id", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("cannot be empty")));
    }

    @Test
    void getLinkInfo_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/link")
                        .param("id", "pub-unauth-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getLinkInfo_nonExistentUrl_returns404NotFound() throws Exception {
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("id", "nonexistent-404-link"))
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

        String publicId = createRes.split("\"publicId\":\"")[1].split("\"")[0];

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
                        .param("id", publicId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    void createWithCustomPathAndAddShortCodeTrue_appendsShortCodeAndSavesCustomPath() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/invoice/order123",
                    "customPath": "invoice",
                    "addShortCode": true
                }
                """;

        String res = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customPath").value("invoice"))
                .andExpect(jsonPath("$.data.newUrl", matchesPattern("^http://localhost:8081/invoice/[a-zA-Z0-9_-]+$")))
                .andReturn().getResponse().getContentAsString();

        String generatedNewUrl = res.split("\"newUrl\":\"")[1].split("\"")[0];
        String generatedCode = generatedNewUrl.substring("http://localhost:8081/invoice/".length());

        // Verify entity in DB
        NewUrl dbEntity = shortUrlRepository.findByNewUrl(generatedNewUrl).orElseThrow();
        assertEquals("invoice", dbEntity.getCustomPath());
        assertEquals(generatedCode, dbEntity.getShortCode());
        assertEquals("https://example.com/invoice/order123", dbEntity.getOriginalUrl());

        // Verify lookup by id parameter
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("id", dbEntity.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newUrl").value(generatedNewUrl))
                .andExpect(jsonPath("$.data.customPath").value("invoice"));
    }

    @Test
    void createWithCustomPathAndAddShortCodeFalse_keepsTillCustomPath() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/winter-fest",
                    "customPath": "winter-fest",
                    "addShortCode": false
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customPath").value("winter-fest"))
                .andExpect(jsonPath("$.data.newUrl").value("http://localhost:8081/winter-fest"));

        // Verify entity in DB
        NewUrl dbEntity = shortUrlRepository.findByNewUrl("http://localhost:8081/winter-fest").orElseThrow();
        assertEquals("winter-fest", dbEntity.getCustomPath());
        assertEquals("winter-fest", dbEntity.getShortCode());
        assertEquals("http://localhost:8081/winter-fest", dbEntity.getNewUrl());

        // Lookup by public id
        mockMvc.perform(get("/link")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("id", dbEntity.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newUrl").value("http://localhost:8081/winter-fest"));
    }

    @Test
    void idempotent_whenActiveNonExpiredNonLimited_returnsExisting() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/idempotent-check"
                }
                """;

        String res1 = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(false))
                .andReturn().getResponse().getContentAsString();

        String newUrl1 = res1.split("\"newUrl\":\"")[1].split("\"")[0];

        // Second call -> should be idempotent and return existing URL
        String res2 = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(true))
                .andReturn().getResponse().getContentAsString();

        String newUrl2 = res2.split("\"newUrl\":\"")[1].split("\"")[0];
        assertEquals(newUrl1, newUrl2, "Should return existing newUrl");
    }

    @Test
    void idempotent_whenExpired_createsNewUrl() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/expired-idempotent-check"
                }
                """;

        String res1 = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(false))
                .andReturn().getResponse().getContentAsString();

        String newUrl1 = res1.split("\"newUrl\":\"")[1].split("\"")[0];

        // Expire the URL in DB
        NewUrl entity1 = shortUrlRepository.findByNewUrl(newUrl1).orElseThrow();
        entity1.setExpireAt(java.time.Instant.now().minusSeconds(60));
        shortUrlRepository.save(entity1);

        // Next call -> condition exhausted, must make new URL
        String res2 = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(false))
                .andReturn().getResponse().getContentAsString();

        String newUrl2 = res2.split("\"newUrl\":\"")[1].split("\"")[0];
        assertNotEquals(newUrl1, newUrl2, "Should create a new URL because previous one expired");
    }

    @Test
    void idempotent_whenClickLimitExceeded_createsNewUrl() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/limit-idempotent-check",
                    "usageLimit": 1
                }
                """;

        String res1 = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(false))
                .andReturn().getResponse().getContentAsString();

        String newUrl1 = res1.split("\"newUrl\":\"")[1].split("\"")[0];

        // Simulate click limit reached
        NewUrl entity1 = shortUrlRepository.findByNewUrl(newUrl1).orElseThrow();
        entity1.setClickCount(1);
        shortUrlRepository.save(entity1);

        // Next call -> click limit exhausted, must make new URL
        String res2 = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(false))
                .andReturn().getResponse().getContentAsString();

        String newUrl2 = res2.split("\"newUrl\":\"")[1].split("\"")[0];
        assertNotEquals(newUrl1, newUrl2, "Should create a new URL because previous one reached click limit");
    }

    @Test
    void idempotent_whenInactive_createsNewUrl() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/inactive-idempotent-check"
                }
                """;

        String res1 = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(false))
                .andReturn().getResponse().getContentAsString();

        String newUrl1 = res1.split("\"newUrl\":\"")[1].split("\"")[0];

        // Deactivate the URL in DB
        NewUrl entity1 = shortUrlRepository.findByNewUrl(newUrl1).orElseThrow();
        entity1.setActive(false);
        shortUrlRepository.save(entity1);

        // Next call -> inactive exhausted, must make new URL
        String res2 = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.existing").value(false))
                .andReturn().getResponse().getContentAsString();

        String newUrl2 = res2.split("\"newUrl\":\"")[1].split("\"")[0];
        assertNotEquals(newUrl1, newUrl2, "Should create a new URL because previous one was deactivated");
    }

    @Test
    void createNewUrl_logsCreatedActionInChangeLog() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/changelog-test"
                }
                """;

        String res = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newUrl = res.split("\"newUrl\":\"")[1].split("\"")[0];
        NewUrl entity = shortUrlRepository.findByNewUrl(newUrl).orElseThrow();

        List<NewUrlChangeLog> logs = changeLogRepository.findByUrlIdOrderByCreatedAtDesc(entity.getId());
        assertFalse(logs.isEmpty());
        NewUrlChangeLog createdLog = logs.stream()
                .filter(l -> "CREATED".equals(l.getAction()))
                .findFirst()
                .orElseThrow();
        assertEquals("ALL", createdLog.getFieldName());
        assertEquals(newUrl, createdLog.getNewValue());
        assertNull(createdLog.getOldValue());
    }

    @Test
    void editNewUrl_updatesParametersAndMaintainsFieldChangeLogs() throws Exception {
        // 1. Create a link
        String createPayload = """
                {
                    "url": "https://example.com/initial-page",
                    "note": "Initial note",
                    "tags": ["initial-tag"],
                    "linkMode": "REDIRECT",
                    "usageLimit": 10
                }
                """;

        String createRes = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newUrl = createRes.split("\"newUrl\":\"")[1].split("\"")[0];
        NewUrl initialEntity = shortUrlRepository.findByNewUrl(newUrl).orElseThrow();
        Long urlId = initialEntity.getId();

        // 2. Edit link with new parameters
        String editPayload = """
                {
                    "newUrl": "%s",
                    "originalUrl": "https://example.com/updated-page",
                    "notes": "Updated note",
                    "tags": ["updated-tag-1", "updated-tag-2"],
                    "linkMode": "IFRAME",
                    "isActive": false,
                    "usageLimit": 25
                }
                """.formatted(newUrl);

        mockMvc.perform(post("/link/edit")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newUrl").value(newUrl))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/updated-page"))
                .andExpect(jsonPath("$.data.notes").value("Updated note"))
                .andExpect(jsonPath("$.data.tags", containsInAnyOrder("updated-tag-1", "updated-tag-2")))
                .andExpect(jsonPath("$.data.linkMode").value("IFRAME"))
                .andExpect(jsonPath("$.data.isActive").value(false))
                .andExpect(jsonPath("$.data.usageLimit").value(25));

        // 3. Verify entity in database
        NewUrl updatedEntity = shortUrlRepository.findById(urlId).orElseThrow();
        assertEquals("https://example.com/updated-page", updatedEntity.getOriginalUrl());
        assertEquals("Updated note", updatedEntity.getNote());
        assertEquals(LinkMode.IFRAME, updatedEntity.getLinkMode());
        assertFalse(updatedEntity.isActive());
        assertEquals(25L, updatedEntity.getUsageLimit());

        // 4. Verify change logs for EDITED
        List<NewUrlChangeLog> editLogs = changeLogRepository.findByUrlIdAndActionOrderByCreatedAtDesc(urlId, "EDITED");
        assertFalse(editLogs.isEmpty());

        // Check original_url log
        NewUrlChangeLog urlLog = editLogs.stream().filter(l -> "original_url".equals(l.getFieldName())).findFirst().orElseThrow();
        assertEquals("https://example.com/initial-page", urlLog.getOldValue());
        assertEquals("https://example.com/updated-page", urlLog.getNewValue());

        // Check notes log
        NewUrlChangeLog noteLog = editLogs.stream().filter(l -> "notes".equals(l.getFieldName())).findFirst().orElseThrow();
        assertEquals("Initial note", noteLog.getOldValue());
        assertEquals("Updated note", noteLog.getNewValue());

        // Check link_mode log
        NewUrlChangeLog modeLog = editLogs.stream().filter(l -> "link_mode".equals(l.getFieldName())).findFirst().orElseThrow();
        assertEquals("REDIRECT", modeLog.getOldValue());
        assertEquals("IFRAME", modeLog.getNewValue());

        // Check is_active log
        NewUrlChangeLog activeLog = editLogs.stream().filter(l -> "is_active".equals(l.getFieldName())).findFirst().orElseThrow();
        assertEquals("true", activeLog.getOldValue());
        assertEquals("false", activeLog.getNewValue());

        // Check usage_limit log
        NewUrlChangeLog limitLog = editLogs.stream().filter(l -> "usage_limit".equals(l.getFieldName())).findFirst().orElseThrow();
        assertEquals("10", limitLog.getOldValue());
        assertEquals("25", limitLog.getNewValue());

        // Check tags log
        NewUrlChangeLog tagLog = editLogs.stream().filter(l -> "tags".equals(l.getFieldName())).findFirst().orElseThrow();
        assertEquals("initial-tag", tagLog.getOldValue());
        assertTrue(tagLog.getNewValue().contains("updated-tag-1") && tagLog.getNewValue().contains("updated-tag-2"));
    }

    @Test
    void editNewUrl_partialUpdatesViaPost() throws Exception {
        String createPayload = """
                {
                    "url": "https://example.com/partial-post-test"
                }
                """;

        String createRes = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newUrl = createRes.split("\"newUrl\":\"")[1].split("\"")[0];

        // 1. Partial update: only note via POST /link/edit
        String notePayload = """
                {
                    "newUrl": "%s",
                    "notes": "Post updated note only"
                }
                """.formatted(newUrl);

        mockMvc.perform(post("/link/edit")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").value("Post updated note only"))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/partial-post-test"));

        // 2. Partial update: target URL via POST /link/edit
        String targetPayload = """
                {
                    "newUrl": "%s",
                    "originalUrl": "https://example.com/partial-post-test-updated"
                }
                """.formatted(newUrl);

        mockMvc.perform(post("/link/edit")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(targetPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/partial-post-test-updated"))
                .andExpect(jsonPath("$.data.notes").value("Post updated note only"));
    }

    @Test
    void editNewUrl_unauthorizedOrWrongUser_forbiddenOrUnauthorized() throws Exception {
        String editPayload = """
                {
                    "newUrl": "http://localhost:8081/non-existent"
                }
                """;

        // Without auth -> 401
        mockMvc.perform(post("/link/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editPayload))
                .andExpect(status().isUnauthorized());

        // With valid key but non-existent link -> 404
        mockMvc.perform(post("/link/edit")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editPayload))
                .andExpect(status().isNotFound());
    }

    @Test
    void editNewUrl_onlyPostAllowed_putReturnsMethodNotAllowed() throws Exception {
        String editPayload = """
                {
                    "newUrl": "http://localhost:8081/test"
                }
                """;

        mockMvc.perform(put("/link/edit")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editPayload))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listUrls_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/link/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUrls_noUrls_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/link/list")
                        .header("X-API-KEY", VALID_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void listUrls_returnsPaginatedListOrderedByLatestDefault() throws Exception {
        Long userId = userRepository.findAll().get(0).getId();

        NewUrl url1 = new NewUrl("code1", "https://example.com/first", null, "http://localhost:8081/code1",
                Instant.now().plus(30, ChronoUnit.DAYS), null, LinkMode.REDIRECT);
        url1.setUserId(userId);
        url1.setActive(true);
        url1.setCreatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        url1.setUpdatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        shortUrlRepository.save(url1);

        NewUrl url2 = new NewUrl("code2", "https://example.com/second", null, "http://localhost:8081/code2",
                Instant.now().plus(30, ChronoUnit.DAYS), 5L, LinkMode.REDIRECT);
        url2.setUserId(userId);
        url2.setActive(false);
        url2.setCreatedAt(Instant.now());
        url2.setUpdatedAt(Instant.now());
        shortUrlRepository.save(url2);

        mockMvc.perform(get("/link/list")
                        .header("X-API-KEY", VALID_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].shortLink").value("http://localhost:8081/code2"))
                .andExpect(jsonPath("$.data.content[0].originalLink").value("https://example.com/second"))
                .andExpect(jsonPath("$.data.content[0].isEnabled").value(false))
                .andExpect(jsonPath("$.data.content[0].isExpired").value(false))
                .andExpect(jsonPath("$.data.content[0].expiredReason").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].shortLink").value("http://localhost:8081/code1"))
                .andExpect(jsonPath("$.data.content[1].originalLink").value("https://example.com/first"))
                .andExpect(jsonPath("$.data.content[1].isEnabled").value(true))
                .andExpect(jsonPath("$.data.content[1].isExpired").value(false));
    }

    @Test
    void listUrls_expiredReasons_timeAndUsage() throws Exception {
        Long userId = userRepository.findAll().get(0).getId();

        // 1. Expired by TIME
        NewUrl urlTime = new NewUrl("time-exp", "https://example.com/time", null, "http://localhost:8081/time-exp",
                Instant.now().minus(1, ChronoUnit.DAYS), null, LinkMode.REDIRECT);
        urlTime.setUserId(userId);
        urlTime.setActive(true);
        urlTime.setCreatedAt(Instant.now().minus(3, ChronoUnit.MINUTES));
        urlTime.setUpdatedAt(Instant.now().minus(3, ChronoUnit.MINUTES));
        shortUrlRepository.save(urlTime);

        // 2. Expired by USAGE
        NewUrl urlUsage = new NewUrl("usage-exp", "https://example.com/usage", null, "http://localhost:8081/usage-exp",
                Instant.now().plus(10, ChronoUnit.DAYS), 5L, LinkMode.REDIRECT);
        urlUsage.setUserId(userId);
        urlUsage.setClickCount(5);
        urlUsage.setActive(true);
        urlUsage.setCreatedAt(Instant.now().minus(2, ChronoUnit.MINUTES));
        urlUsage.setUpdatedAt(Instant.now().minus(2, ChronoUnit.MINUTES));
        shortUrlRepository.save(urlUsage);

        // 3. Expired by BOTH
        NewUrl urlBoth = new NewUrl("both-exp", "https://example.com/both", null, "http://localhost:8081/both-exp",
                Instant.now().minus(1, ChronoUnit.DAYS), 2L, LinkMode.REDIRECT);
        urlBoth.setUserId(userId);
        urlBoth.setClickCount(2);
        urlBoth.setActive(true);
        urlBoth.setCreatedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        urlBoth.setUpdatedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        shortUrlRepository.save(urlBoth);

        // Test with /link/urls alias as well
        mockMvc.perform(get("/link/urls")
                        .header("X-API-KEY", VALID_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                // Latest first: both-exp
                .andExpect(jsonPath("$.data.content[0].shortLink").value("http://localhost:8081/both-exp"))
                .andExpect(jsonPath("$.data.content[0].isExpired").value(true))
                .andExpect(jsonPath("$.data.content[0].expiredReason").value("TIME, USAGE"))
                .andExpect(jsonPath("$.data.content[0].why").value("TIME, USAGE"))
                // Next: usage-exp
                .andExpect(jsonPath("$.data.content[1].shortLink").value("http://localhost:8081/usage-exp"))
                .andExpect(jsonPath("$.data.content[1].isExpired").value(true))
                .andExpect(jsonPath("$.data.content[1].expiredReason").value("USAGE"))
                .andExpect(jsonPath("$.data.content[1].why").value("USAGE"))
                // Next: time-exp
                .andExpect(jsonPath("$.data.content[2].shortLink").value("http://localhost:8081/time-exp"))
                .andExpect(jsonPath("$.data.content[2].isExpired").value(true))
                .andExpect(jsonPath("$.data.content[2].expiredReason").value("TIME"))
                .andExpect(jsonPath("$.data.content[2].why").value("TIME"));
    }

    @Test
    void listUrls_paginationParamsSupported() throws Exception {
        Long userId = userRepository.findAll().get(0).getId();

        for (int i = 1; i <= 5; i++) {
            NewUrl url = new NewUrl("code-" + i, "https://example.com/" + i, null, "http://localhost:8081/code-" + i,
                    Instant.now().plus(30, ChronoUnit.DAYS), null, LinkMode.REDIRECT);
            url.setUserId(userId);
            url.setActive(true);
            url.setCreatedAt(Instant.now().plusSeconds(i * 10));
            url.setUpdatedAt(Instant.now().plusSeconds(i * 10));
            shortUrlRepository.save(url);
        }

        // Page 0, Size 2
        mockMvc.perform(get("/link/list")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.numberOfElements").value(2))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.content[0].shortLink").value("http://localhost:8081/code-5"))
                .andExpect(jsonPath("$.data.content[1].shortLink").value("http://localhost:8081/code-4"));

        // Page 1, Size 2
        mockMvc.perform(get("/link/list")
                        .header("X-API-KEY", VALID_API_KEY)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.numberOfElements").value(2))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.content[0].shortLink").value("http://localhost:8081/code-3"))
                .andExpect(jsonPath("$.data.content[1].shortLink").value("http://localhost:8081/code-2"));
    }

    @Test
    void createProxyMode_webpageUrl_succeeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/products/index.html",
                    "linkMode": "PROXY"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.linkMode").value("PROXY"))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/products/index.html"));
    }

    @Test
    void createProxyMode_resourceUrl_succeeds() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/files/dataset.zip",
                    "linkMode": "PROXY"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.linkMode").value("PROXY"))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/files/dataset.zip"));
    }

    @Test
    void createProxyMode_ssrfOrInvalidScheme_returns400() throws Exception {
        String[] invalidUrls = {
                "http://127.0.0.1/admin",
                "ftp://example.com/file.zip",
                "file:///etc/passwd",
                "https://169.254.169.254/latest/meta-data/"
        };

        for (String invalidUrl : invalidUrls) {
            String payload = """
                    {
                        "url": "%s",
                        "linkMode": "PROXY"
                    }
                    """.formatted(invalidUrl);

            mockMvc.perform(post("/link/create")
                            .header("X-API-KEY", VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    void editProxyMode_webpageUrl_succeeds() throws Exception {
        // Create REDIRECT URL
        String createPayload = """
                {
                    "url": "https://example.com/landing",
                    "linkMode": "REDIRECT",
                    "customPath": "landing-page"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk());

        // Edit to PROXY with a webpage URL -> succeeds in generic reverse proxy
        String editPayload = """
                {
                    "newUrl": "http://localhost:8081/landing-page",
                    "linkMode": "PROXY"
                }
                """;

        mockMvc.perform(post("/link/edit")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.linkMode").value("PROXY"));

        // Edit with invalid SSRF URL and PROXY mode -> 400 Bad Request
        String invalidEditPayload = """
                {
                    "newUrl": "http://localhost:8081/landing-page",
                    "originalUrl": "http://127.0.0.1:8080/internal",
                    "linkMode": "PROXY"
                }
                """;

        mockMvc.perform(post("/link/edit")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEditPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listAccessLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/link/pub-unauth/accessLog"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAccessLogs_byPublicId_orderedByLatestUp() throws Exception {
        Long userId = userRepository.findAll().get(0).getId();

        NewUrl url1 = new NewUrl("logCode1", "https://example.com/item1", null, "http://localhost:8081/logCode1",
                Instant.now().plus(30, ChronoUnit.DAYS), null, LinkMode.REDIRECT);
        url1.setUserId(userId);
        url1 = shortUrlRepository.save(url1);

        NewUrl url2 = new NewUrl("logCode2", "https://example.com/item2", null, "http://localhost:8081/logCode2",
                Instant.now().plus(30, ChronoUnit.DAYS), null, LinkMode.REDIRECT);
        url2.setUserId(userId);
        url2 = shortUrlRepository.save(url2);

        LocalDateTime t1 = LocalDateTime.now().minusHours(3);
        LocalDateTime t2 = LocalDateTime.now().minusHours(2);
        LocalDateTime t3 = LocalDateTime.now().minusHours(1);

        NewUrlAccessLog log1 = new NewUrlAccessLog(url1.getId(), url1.getShortCode(), "1.1.1.1", "Agent1", "ref1");
        log1.setAccessedAt(t1);
        accessLogRepository.save(log1);

        NewUrlAccessLog log2 = new NewUrlAccessLog(url2.getId(), url2.getShortCode(), "2.2.2.2", "Agent2", "ref2");
        log2.setAccessedAt(t2);
        accessLogRepository.save(log2);

        NewUrlAccessLog log3 = new NewUrlAccessLog(url1.getId(), url1.getShortCode(), "3.3.3.3", "Agent3", "ref3");
        log3.setAccessedAt(t3);
        accessLogRepository.save(log3);

        mockMvc.perform(get("/link/" + url1.getPublicId() + "/accessLog")
                        .header("X-API-KEY", VALID_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                // Ordered by latest up (descending)
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("3.3.3.3"))
                .andExpect(jsonPath("$.data.content[0].shortCode").value("logCode1"))
                .andExpect(jsonPath("$.data.content[1].ipAddress").value("1.1.1.1"))
                .andExpect(jsonPath("$.data.content[1].shortCode").value("logCode1"));
    }

    @Test
    void listAccessLogs_byPathVariable_success() throws Exception {
        Long userId = userRepository.findAll().get(0).getId();

        NewUrl url = new NewUrl("pathCode", "https://example.com/path", null, "http://localhost:8081/pathCode",
                Instant.now().plus(30, ChronoUnit.DAYS), null, LinkMode.REDIRECT);
        url.setUserId(userId);
        url = shortUrlRepository.save(url);

        NewUrlAccessLog log = new NewUrlAccessLog(url.getId(), url.getShortCode(), "192.168.1.1", "Agent", "ref");
        log.setAccessedAt(LocalDateTime.now());
        accessLogRepository.save(log);

        mockMvc.perform(get("/link/" + url.getPublicId() + "/accessLog")
                        .header("X-API-KEY", VALID_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("192.168.1.1"))
                .andExpect(jsonPath("$.data.content[0].shortCode").value("pathCode"));
    }

    @Test
    void listAccessLogs_notFound_returns404() throws Exception {
        mockMvc.perform(get("/link/unknownCode123/accessLog")
                        .header("X-API-KEY", VALID_API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("URL not found"));
    }

    @Test
    void listAccessLogs_otherUserLink_returns403() throws Exception {
        Long userId = userRepository.findAll().get(0).getId();

        NewUrl url = new NewUrl("otherUserCode", "https://example.com/other-user", null, "http://localhost:8081/otherUserCode",
                Instant.now().plus(30, ChronoUnit.DAYS), null, LinkMode.REDIRECT);
        url.setUserId(userId);
        url = shortUrlRepository.save(url);

        Tenant tenant2 = tenantRepository.save(new Tenant("AccessLog Tenant 2"));
        User user2 = userRepository.save(new User(tenant2.getId(), "AccessLog User 2", "accessloguser2", "pass123"));
        String USER2_API_KEY = "user2-accesslog-key-99999";

        ApiKey apiKey2 = new ApiKey();
        apiKey2.setUserId(user2.getId());
        apiKey2.setName("User2 AccessLog Key");
        apiKey2.setApiKeyHash(sha256(USER2_API_KEY));
        apiKey2.setActive(true);
        apiKeyRepository.save(apiKey2);

        mockMvc.perform(get("/link/" + url.getPublicId() + "/accessLog")
                        .header("X-API-KEY", USER2_API_KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createNewUrl_withVerifiedCustomDomain_savesCustomDomainAndNewUrl() throws Exception {
        User user = userRepository.findAll().get(0);
        CustomDomain domain = new CustomDomain(user.getId(), "links.mybrand.com", "go.domain.com");
        domain.setStatus(DomainStatus.ACTIVE);
        customDomainRepository.save(domain);

        String json = """
                {
                    "url": "https://example.com/custom-branded",
                    "domain": "links.mybrand.com"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newUrl", startsWith("https://links.mybrand.com/")));

        NewUrl saved = shortUrlRepository.findAll().stream()
                .filter(u -> "https://example.com/custom-branded".equals(u.getOriginalUrl()))
                .findFirst()
                .orElseThrow();

        assertEquals("links.mybrand.com", saved.getDomain());
        assertTrue(saved.getNewUrl().startsWith("https://links.mybrand.com/"));
    }

    @Test
    void createNewUrl_withUnverifiedCustomDomain_returnsBadRequest() throws Exception {
        User user = userRepository.findAll().get(0);
        CustomDomain domain = new CustomDomain(user.getId(), "unverified.mybrand.com", "go.domain.com");
        domain.setStatus(DomainStatus.VERIFICATION_REQUIRED);
        customDomainRepository.save(domain);

        String json = """
                {
                    "url": "https://example.com/pending",
                    "domain": "unverified.mybrand.com"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("not verified")));
    }

    @Test
    void createNewUrl_withOtherUserDomain_returnsBadRequest() throws Exception {
        CustomDomain domain = new CustomDomain(99999L, "other.mybrand.com", "go.domain.com");
        domain.setStatus(DomainStatus.ACTIVE);
        customDomainRepository.save(domain);

        String json = """
                {
                    "url": "https://example.com/stolen",
                    "domain": "other.mybrand.com"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("does not belong")));
    }
}

