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

    @Test
    void ipRestrictedLink_whenIpForbidden_showsProperWebpageWithReasonAndDetails() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/target-internal-system",
                    "customPath": "corp-only",
                    "accessPolicies": {
                        "mode": "SECURED",
                        "ipAllowlist": {
                            "addresses": ["192.168.10.5", "10.0.0.0/16"]
                        }
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Visit with unauthorized IP
        MvcResult result = mockMvc.perform(get("/corp-only")
                        .header("Accept", "text/html,application/xhtml+xml,*/*")
                        .header("X-Forwarded-For", "203.0.113.195"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"exhausted-container\"")))
                .andExpect(content().string(containsString("Access Restricted: IP Not Allowed")))
                .andExpect(content().string(containsString("IP Restricted")))
                .andExpect(content().string(containsString("203.0.113.195")))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("Your IP Address"), "Should show client IP diagnostic key");
    }

    @Test
    void countryRestrictedLink_whenCountryBlocked_showsProperWebpageWithReasonAndDetails() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/us-only-promo",
                    "customPath": "us-promo",
                    "accessPolicies": {
                        "mode": "SECURED",
                        "country": {
                            "countries": ["US", "CA"]
                        }
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Visit from FR
        mockMvc.perform(get("/us-promo")
                        .header("Accept", "text/html,application/xhtml+xml,*/*")
                        .header("CF-IPCountry", "FR"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"exhausted-container\"")))
                .andExpect(content().string(containsString("Access Restricted: Country Restriction")))
                .andExpect(content().string(containsString("Country Blocked")))
                .andExpect(content().string(containsString("FR")))
                .andExpect(content().string(containsString("US,CA")));
    }

    @Test
    void deviceRestrictedLink_whenDeviceIncompatible_showsProperWebpageWithReasonAndDetails() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/app-download",
                    "customPath": "mobile-app-link",
                    "accessPolicies": {
                        "mode": "SECURED",
                        "device": {
                            "devices": ["MOBILE"]
                        }
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Visit with Desktop browser User-Agent
        mockMvc.perform(get("/mobile-app-link")
                        .header("Accept", "text/html,application/xhtml+xml,*/*")
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"exhausted-container\"")))
                .andExpect(content().string(containsString("Access Restricted: Device Incompatible")))
                .andExpect(content().string(containsString("Device Incompatible")))
                .andExpect(content().string(containsString("Desktop (macOS)")));
    }

    @Test
    void referrerRestrictedLink_whenReferrerBlocked_showsProperWebpageWithReasonAndDetails() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/partner-portal",
                    "customPath": "partner-link",
                    "accessPolicies": {
                        "mode": "SECURED",
                        "referrer": {
                            "referrers": ["*.authorized-partner.com"]
                        }
                    }
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Visit without Referer (Direct visit)
        mockMvc.perform(get("/partner-link")
                        .header("Accept", "text/html,application/xhtml+xml,*/*"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"exhausted-container\"")))
                .andExpect(content().string(containsString("Access Restricted: Unauthorized Referrer")))
                .andExpect(content().string(containsString("Referrer Blocked")))
                .andExpect(content().string(containsString("Direct Access (No Referer)")));
    }

    @Test
    void editLink_fetchesDetailsAndConfig_populatesProperlyAndUpdatesCorrectly() throws Exception {
        // 1. Create a link initially with unlimited usage and public access
        String createPayload = """
                {
                    "url": "https://example.com/initial-destination",
                    "customPath": "edit-flow-link",
                    "notes": "Initial notes",
                    "tags": ["test", "v1"]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId").isNotEmpty())
                .andExpect(jsonPath("$.data.usagePolicies.type").value("UNLIMITED"))
                .andExpect(jsonPath("$.data.accessPolicies.mode").value("PUBLIC"))
                .andReturn();

        String publicId = com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.publicId");
        String shortUrl = com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.newUrl");

        // 2. Fetch all details and conf via GET /link?id={publicId}
        mockMvc.perform(get("/link")
                        .param("id", publicId)
                        .header("X-API-KEY", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/initial-destination"))
                .andExpect(jsonPath("$.data.customPath").value("edit-flow-link"))
                .andExpect(jsonPath("$.data.notes").value("Initial notes"))
                .andExpect(jsonPath("$.data.tags", hasItems("test", "v1")))
                .andExpect(jsonPath("$.data.usagePolicies.type").value("UNLIMITED"))
                .andExpect(jsonPath("$.data.accessPolicies.mode").value("PUBLIC"));

        // 3. Edit the link with full usagePolicies & accessPolicies
        Instant startWindow = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant endWindow = Instant.now().plus(48, ChronoUnit.HOURS);
        Instant expireTime = Instant.now().plus(72, ChronoUnit.HOURS);

        String editPayload = String.format("""
                {
                    "newUrl": "%s",
                    "originalUrl": "https://example.com/updated-destination",
                    "notes": "Updated campaign notes",
                    "tags": ["marketing", "updated"],
                    "linkMode": "REDIRECT",
                    "isActive": true,
                    "usagePolicies": {
                        "type": "USAGE_LIMIT",
                        "usageLimit": 50,
                        "expireAt": "%s",
                        "schedule": {
                            "startAt": "%s",
                            "endAt": "%s"
                        }
                    },
                    "accessPolicies": {
                        "mode": "SECURED",
                        "pin": { "pin": "987654" },
                        "country": { "countries": ["US", "IN"] },
                        "device": { "devices": ["MOBILE", "DESKTOP"] },
                        "ipAllowlist": { "addresses": ["10.0.0.0/16", "192.168.1.1"] },
                        "referrer": { "referrers": ["*.example.com"] }
                    }
                }
                """, shortUrl, expireTime, startWindow, endWindow);

        mockMvc.perform(post("/link/edit")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/updated-destination"))
                .andExpect(jsonPath("$.data.notes").value("Updated campaign notes"))
                .andExpect(jsonPath("$.data.tags", hasItems("marketing", "updated")))
                .andExpect(jsonPath("$.data.usagePolicies.type").value("USAGE_LIMIT"))
                .andExpect(jsonPath("$.data.usagePolicies.usageLimit").value(50))
                .andExpect(jsonPath("$.data.accessPolicies.mode").value("SECURED"))
                .andExpect(jsonPath("$.data.accessPolicies.pin.pin").value("******"))
                .andExpect(jsonPath("$.data.accessPolicies.country.countries", hasItems("US", "IN")))
                .andExpect(jsonPath("$.data.accessPolicies.device.devices", hasItems("MOBILE", "DESKTOP")))
                .andExpect(jsonPath("$.data.accessPolicies.ipAllowlist.addresses", hasItems("10.0.0.0/16", "192.168.1.1")))
                .andExpect(jsonPath("$.data.accessPolicies.referrer.referrers", hasItems("*.example.com")));

        // 4. Fetch details again via GET /link?id={publicId} to verify retrieval for Edit Modal
        mockMvc.perform(get("/link")
                        .param("id", publicId)
                        .header("X-API-KEY", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/updated-destination"))
                .andExpect(jsonPath("$.data.usagePolicies.type").value("USAGE_LIMIT"))
                .andExpect(jsonPath("$.data.usagePolicies.usageLimit").value(50))
                .andExpect(jsonPath("$.data.usagePolicies.schedule.startAt").isNotEmpty())
                .andExpect(jsonPath("$.data.usagePolicies.schedule.endAt").isNotEmpty())
                .andExpect(jsonPath("$.data.accessPolicies.mode").value("SECURED"))
                .andExpect(jsonPath("$.data.accessPolicies.pin.pin").value("******"))
                .andExpect(jsonPath("$.data.accessPolicies.country.countries", hasItems("US", "IN")))
                .andExpect(jsonPath("$.data.accessPolicies.device.devices", hasItems("MOBILE", "DESKTOP")))
                .andExpect(jsonPath("$.data.accessPolicies.ipAllowlist.addresses", hasItems("10.0.0.0/16", "192.168.1.1")))
                .andExpect(jsonPath("$.data.accessPolicies.referrer.referrers", hasItems("*.example.com")));

        // 5. Subsequent edit keeping masked PIN "******" preserves existing PIN hash
        String editKeepMaskPayload = String.format("""
                {
                    "newUrl": "%s",
                    "originalUrl": "https://example.com/updated-destination-2",
                    "accessPolicies": {
                        "mode": "SECURED",
                        "pin": { "pin": "******" },
                        "country": { "countries": ["US", "IN"] }
                    }
                }
                """, shortUrl);

        mockMvc.perform(post("/link/edit")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editKeepMaskPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/updated-destination-2"))
                .andExpect(jsonPath("$.data.accessPolicies.pin.pin").value("******"));

        AccessPolicy ap = accessPolicyRepository.findAll().stream().findFirst().orElseThrow();
        assertTrue(passwordEncoder.matches("987654", ap.getPinHash()));
    }
}
