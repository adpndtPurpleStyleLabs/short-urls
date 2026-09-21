package com.preonsurl.apis;

import com.preonsurl.apis.apikey.APIKeyCache;
import com.preonsurl.apis.apikey.ApiKey;
import com.preonsurl.apis.apikey.ApiKeyRepository;
import com.preonsurl.apis.auth.cache.UserCache;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.UsagePolicy;
import com.preonsurl.apis.link.enums.AccessPolicyMode;
import com.preonsurl.apis.link.enums.UsagePolicyType;
import com.preonsurl.apis.link.repository.AccessPolicyRepository;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.repository.UsagePolicyRepository;
import com.preonsurl.apis.link.service.NewUrlServingCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccessAndUsagePoliciesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewUrlRepository shortUrlRepository;

    @Autowired
    private AccessPolicyRepository accessPolicyRepository;

    @Autowired
    private UsagePolicyRepository usagePolicyRepository;

    @Autowired
    private NewUrlAccessLogRepository accessLogRepository;

    @Autowired
    private NewUrlServingCacheService servingCacheService;

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String API_KEY = "policy-test-api-key";

    @BeforeEach
    void setUp() {
        servingCacheService.getLruCache().clear();
        apiKeyCache.clear();
        userCache.clear();
        accessPolicyRepository.deleteAll();
        usagePolicyRepository.deleteAll();
        accessLogRepository.deleteAll();
        shortUrlRepository.deleteAll();
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = tenantRepository.save(new Tenant("Policy Tenant"));
        User user = userRepository.save(new User(tenant.getId(), "Policy User", "policyuser", "hashedpass"));

        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(user.getId());
        apiKey.setName("Policy Key");
        apiKey.setApiKeyHash(sha256(API_KEY));
        apiKey.setActive(true);
        apiKeyRepository.save(apiKey);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createUrlWithUsageAndAccessPoliciesPersistsInBothTables() throws Exception {
        Instant futureExpiry = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        String payload = """
                {
                    "url": "https://example.com/secured-resource",
                    "customPath": "secured-report",
                    "usagePolicies": {
                        "type": "USAGE_LIMIT",
                        "usageLimit": 25,
                        "expireAt": "%s"
                    },
                    "accessPolicies": {
                        "mode": "SECURED",
                        "pin": { "pin": "123456" },
                        "password": { "password": "SecretPass123" }
                    }
                }
                """.formatted(futureExpiry.toString());

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customPath").value("secured-report"));

        NewUrl savedUrl = shortUrlRepository.findByCustomPath("secured-report").orElseThrow();

        // 1. Verify UsagePolicy record in usage_policies table
        UsagePolicy usagePolicy = usagePolicyRepository.findByShortUrlId(savedUrl.getId()).orElseThrow();
        assertEquals(UsagePolicyType.USAGE_LIMIT, usagePolicy.getPolicyType());
        assertEquals(25L, usagePolicy.getUsageLimit());
        assertEquals(0L, usagePolicy.getCurrentUsage());
        assertEquals(futureExpiry, usagePolicy.getExpireAt());

        // 2. Verify AccessPolicy record in access_policies table
        AccessPolicy accessPolicy = accessPolicyRepository.findByShortUrlId(savedUrl.getId()).orElseThrow();
        assertEquals(AccessPolicyMode.SECURED, accessPolicy.getMode());
        assertTrue(accessPolicy.hasPin());
        assertTrue(passwordEncoder.matches("123456", accessPolicy.getPinHash()));
        assertTrue(accessPolicy.hasPassword());
        assertTrue(passwordEncoder.matches("SecretPass123", accessPolicy.getPasswordHash()));
    }

    @Test
    void createSecuredWithoutAnyPolicyFailsValidation() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/invalid-secured",
                    "accessPolicies": {
                        "mode": "SECURED"
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Secured access policy requires at least one restriction configured")));
    }

    @Test
    void createUsageLimitWithoutLimitFailsValidation() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/invalid-limit",
                    "usagePolicies": {
                        "type": "USAGE_LIMIT"
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("usageLimit is required")));
    }

    @Test
    void createWithInvalidScheduleFailsValidation() throws Exception {
        Instant now = Instant.now();
        Instant endAt = now.plus(1, ChronoUnit.HOURS);
        Instant startAt = now.plus(2, ChronoUnit.HOURS); // startAt after endAt

        String payload = """
                {
                    "url": "https://example.com/schedule-test",
                    "usagePolicies": {
                        "type": "UNLIMITED",
                        "schedule": {
                            "startAt": "%s",
                            "endAt": "%s"
                        }
                    }
                }
                """.formatted(startAt, endAt);

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("schedule.endAt must be greater than schedule.startAt")));
    }

    @Test
    void pinSecuredLink_firstShowPageAskPin_thenAllowsOnValidPin() throws Exception {
        String createPayload = """
                {
                    "url": "https://example.com/target-pin-protected",
                    "customPath": "my-pin-link",
                    "accessPolicies": {
                        "mode": "SECURED",
                        "pin": { "pin": "9876" }
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk());

        NewUrl savedUrl = shortUrlRepository.findByCustomPath("my-pin-link").orElseThrow();

        // 1. First visit GET /my-pin-link -> Must show PIN entry webpage
        MvcResult getResult = mockMvc.perform(get("/my-pin-link"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();

        String html = getResult.getResponse().getContentAsString();
        assertTrue(html.contains("PIN Protected Link"), "Should show PIN prompt title");
        assertTrue(html.contains("id=\"pin-input\""), "Should contain PIN input field");
        assertFalse(html.contains("id=\"password-input\""), "Should NOT contain Password input field");
        assertTrue(html.contains("id=\"unlock-submit-btn\""), "Should contain Unlock button");

        // 2. Submit invalid PIN -> Should return 401 with error message on the page
        mockMvc.perform(post("/my-pin-link/verify")
                        .param("pin", "0000"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Invalid PIN. Please try again.")));

        assertEquals(0, usagePolicyRepository.findByShortUrlId(savedUrl.getId()).orElseThrow().getCurrentUsage());

        // 3. Submit valid PIN -> Should redirect 302 to destination target URL and track usage
        mockMvc.perform(post("/my-pin-link/verify")
                        .param("pin", "9876"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target-pin-protected"))
                .andExpect(cookie().exists("PREONS_SEC_" + savedUrl.getId()));

        // 4. Verify usage tracked in usage_policies after successful serve
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            UsagePolicy updatedPolicy = usagePolicyRepository.findByShortUrlId(savedUrl.getId()).orElseThrow();
            assertEquals(1L, updatedPolicy.getCurrentUsage(), "Usage should be incremented to 1");

            NewUrl updatedUrl = shortUrlRepository.findById(savedUrl.getId()).orElseThrow();
            assertEquals(1L, updatedUrl.getClickCount(), "Click count should be incremented to 1");

            assertEquals(1, accessLogRepository.findByShortUrlIdOrderByAccessedAtDesc(savedUrl.getId()).size());
        });
    }

    @Test
    void passwordSecuredLink_firstShowPageAskPassword_thenAllowsOnValidPassword() throws Exception {
        String createPayload = """
                {
                    "url": "https://example.com/target-password-protected",
                    "customPath": "my-pass-link",
                    "accessPolicies": {
                        "mode": "SECURED",
                        "password": { "password": "SuperSecretPassword123" }
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk());

        NewUrl savedUrl = shortUrlRepository.findByCustomPath("my-pass-link").orElseThrow();

        // 1. First visit GET -> Shows password challenge page
        MvcResult getResult = mockMvc.perform(get("/my-pass-link"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();

        String html = getResult.getResponse().getContentAsString();
        assertTrue(html.contains("Password Protected Link"));
        assertTrue(html.contains("id=\"password-input\""));
        assertFalse(html.contains("id=\"pin-input\""));

        // 2. Submit wrong password -> 401
        mockMvc.perform(post("/my-pass-link/verify")
                        .param("password", "WrongPassword"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Invalid Password. Please try again.")));

        // 3. Submit correct password -> 302 Found
        mockMvc.perform(post("/my-pass-link/verify")
                        .param("password", "SuperSecretPassword123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target-password-protected"));

        // 4. Verify usage tracked
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            UsagePolicy up = usagePolicyRepository.findByShortUrlId(savedUrl.getId()).orElseThrow();
            assertEquals(1L, up.getCurrentUsage());
        });
    }

    @Test
    void exhaustedUsageLimit_showsProperWebpageExplainingLinkNotAccessible() throws Exception {
        String createPayload = """
                {
                    "url": "https://example.com/one-time-dest",
                    "customPath": "single-access",
                    "usagePolicies": {
                        "type": "ONE_TIME"
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk());

        // 1st serve -> Succeeds
        mockMvc.perform(get("/single-access"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/one-time-dest"));

        // Wait for async serve event to complete and increment usage in DB
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl updated = shortUrlRepository.findByCustomPath("single-access").orElseThrow();
            assertEquals(1L, updated.getClickCount());
        });

        // 2nd serve with browser Accept text/html -> Shows Exhausted Webpage
        mockMvc.perform(get("/single-access")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"exhausted-container\"")))
                .andExpect(content().string(containsString("Usage Limit Reached")))
                .andExpect(content().string(containsString("This URL has reached the end of its active lifecycle.")));
    }


    @Test
    void expiredLink_showsProperWebpageExplainingLinkExpired() throws Exception {
        NewUrl expiredUrl = new NewUrl(
                "expired-demo",
                "https://example.com/expired-target",
                "http://localhost:8081/expired-demo",
                Instant.now().minus(2, ChronoUnit.HOURS)
        );
        NewUrl saved = shortUrlRepository.save(expiredUrl);

        UsagePolicy up = new UsagePolicy(
                saved.getId(),
                UsagePolicyType.UNLIMITED,
                null,
                expiredUrl.getExpireAt(),
                null,
                null
        );
        usagePolicyRepository.save(up);

        mockMvc.perform(get("/expired-demo")
                        .header("Accept", "text/html,application/xhtml+xml,*/*"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Link Has Expired")))
                .andExpect(content().string(containsString("is no longer accessible.")));
    }
}
