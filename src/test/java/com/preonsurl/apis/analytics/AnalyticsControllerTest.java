package com.preonsurl.apis.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.preonsurl.apis.apikey.APIKeyCache;
import com.preonsurl.apis.apikey.ApiKeyRepository;
import com.preonsurl.apis.auth.JwtService;
import com.preonsurl.apis.auth.cache.UserCache;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewUrlAccessLogRepository accessLogRepository;

    @Autowired
    private NewUrlRepository newUrlRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private APIKeyCache apiKeyCache;

    @Autowired
    private UserCache userCache;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private User user1;
    private User user2;
    private String jwtTokenUser1;
    private String jwtTokenUser2;

    private NewUrl user1Link1;
    private NewUrl user1Link2;
    private NewUrl user2Link1;

    @BeforeEach
    void setUp() {
        accessLogRepository.deleteAll();
        newUrlRepository.deleteAll();
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
        apiKeyCache.clear();
        userCache.clear();

        Tenant tenant = tenantRepository.save(new Tenant("Analytics Tenant"));
        user1 = userRepository.save(new User(tenant.getId(), "User One", "user1", passwordEncoder.encode("Secret123!")));
        user2 = userRepository.save(new User(tenant.getId(), "User Two", "user2", passwordEncoder.encode("Secret123!")));

        jwtTokenUser1 = jwtService.generateToken(user1);
        jwtTokenUser2 = jwtService.generateToken(user2);

        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);

        user1Link1 = new NewUrl("code1", "https://example.com/page1", null, "http://localhost:8081/code1", future, null, LinkMode.REDIRECT);
        user1Link1.setUserId(user1.getId());
        user1Link1 = newUrlRepository.save(user1Link1);

        user1Link2 = new NewUrl("code2", "https://example.com/page2", null, "http://localhost:8081/code2", future, null, LinkMode.REDIRECT);
        user1Link2.setUserId(user1.getId());
        user1Link2 = newUrlRepository.save(user1Link2);

        user2Link1 = new NewUrl("code3", "https://example.com/other", null, "http://localhost:8081/code3", future, null, LinkMode.REDIRECT);
        user2Link1.setUserId(user2.getId());
        user2Link1 = newUrlRepository.save(user2Link1);
    }

    private void createAccessLog(NewUrl link, LocalDateTime timestamp) {
        NewUrlAccessLog log = new NewUrlAccessLog(link.getId(), link.getShortCode(), "127.0.0.1", "TestAgent", "https://google.com");
        log.setAccessedAt(timestamp);
        accessLogRepository.save(log);
    }

    @Test
    void getOverallClicks_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/analytics/overall"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOverallClicks_defaultRange_aggregatesUserClicksAndZeroFills() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate threeDaysAgo = today.minusDays(3);

        // User1 clicks: 2 today on link1, 1 yesterday on link2, 3 three days ago on link1
        createAccessLog(user1Link1, today.atTime(10, 0));
        createAccessLog(user1Link1, today.atTime(14, 30));
        createAccessLog(user1Link2, yesterday.atTime(11, 0));
        createAccessLog(user1Link1, threeDaysAgo.atTime(9, 15));
        createAccessLog(user1Link1, threeDaysAgo.atTime(12, 0));
        createAccessLog(user1Link1, threeDaysAgo.atTime(16, 45));

        // User2 click: 5 clicks today on user2Link1 (should NOT appear in user1's analytics)
        for (int i = 0; i < 5; i++) {
            createAccessLog(user2Link1, today.atTime(10, i));
        }

        MvcResult result = mockMvc.perform(get("/api/analytics/overall")
                        .header("Authorization", "Bearer " + jwtTokenUser1)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalClicks").value(6))
                .andExpect(jsonPath("$.data.startDate").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.data.endDate").value(today.toString()))
                .andExpect(jsonPath("$.data.dailyClicks", hasSize(30)))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode dailyClicks = json.get("data").get("dailyClicks");

        // Verify specific days
        for (JsonNode dayNode : dailyClicks) {
            String date = dayNode.get("date").asText();
            long clicks = dayNode.get("clicks").asLong();
            if (date.equals(today.toString())) {
                assertEquals(2, clicks);
            } else if (date.equals(yesterday.toString())) {
                assertEquals(1, clicks);
            } else if (date.equals(threeDaysAgo.toString())) {
                assertEquals(3, clicks);
            } else {
                assertEquals(0, clicks, "Expected 0 clicks for inactive date: " + date);
            }
        }
    }

    @Test
    void getOverallClicks_customDateRange_filtersAccurately() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate d1 = today.minusDays(5);
        LocalDate d2 = today.minusDays(3);

        createAccessLog(user1Link1, d1.atTime(8, 0));
        createAccessLog(user1Link1, d2.atTime(12, 0));
        createAccessLog(user1Link1, today.atTime(15, 0)); // outside custom range d1..d2

        mockMvc.perform(get("/api/analytics/clicks")
                        .header("Authorization", "Bearer " + jwtTokenUser1)
                        .param("startDate", d1.toString())
                        .param("endDate", d2.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalClicks").value(2))
                .andExpect(jsonPath("$.data.startDate").value(d1.toString()))
                .andExpect(jsonPath("$.data.endDate").value(d2.toString()))
                .andExpect(jsonPath("$.data.dailyClicks", hasSize(3))); // d1, d1+1, d2
    }

    @Test
    void getOverallClicks_invalidDateRange_returns400() throws Exception {
        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/analytics/overall")
                        .header("Authorization", "Bearer " + jwtTokenUser1)
                        .param("startDate", today.toString())
                        .param("endDate", today.minusDays(1).toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("startDate cannot be after endDate")));
    }

    @Test
    void getUrlClicks_byShortCodeParam_success() throws Exception {
        LocalDate today = LocalDate.now();
        createAccessLog(user1Link1, today.atTime(10, 0));
        createAccessLog(user1Link1, today.atTime(11, 0));
        createAccessLog(user1Link2, today.atTime(12, 0)); // link2 click, should not be in link1

        mockMvc.perform(get("/api/analytics/url")
                        .header("Authorization", "Bearer " + jwtTokenUser1)
                        .param("shortCode", user1Link1.getShortCode())
                        .param("startDate", today.minusDays(2).toString())
                        .param("endDate", today.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").value("code1"))
                .andExpect(jsonPath("$.data.newUrl").value("http://localhost:8081/code1"))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/page1"))
                .andExpect(jsonPath("$.data.totalClicks").value(2))
                .andExpect(jsonPath("$.data.dailyClicks", hasSize(3)));
    }

    @Test
    void getUrlClicks_byPathVariable_success() throws Exception {
        LocalDate today = LocalDate.now();
        createAccessLog(user1Link2, today.atTime(10, 0));

        mockMvc.perform(get("/api/analytics/url/{identifier}", user1Link2.getShortCode())
                        .header("Authorization", "Bearer " + jwtTokenUser1)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").value("code2"))
                .andExpect(jsonPath("$.data.totalClicks").value(1));
    }

    @Test
    void getUrlClicks_byFullUrlParam_success() throws Exception {
        LocalDate today = LocalDate.now();
        createAccessLog(user1Link1, today.atTime(14, 0));

        mockMvc.perform(get("/api/analytics/url")
                        .header("Authorization", "Bearer " + jwtTokenUser1)
                        .param("url", user1Link1.getNewUrl())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").value("code1"))
                .andExpect(jsonPath("$.data.totalClicks").value(1));
    }

    @Test
    void getUrlClicks_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/analytics/url")
                        .header("Authorization", "Bearer " + jwtTokenUser1)
                        .param("shortCode", "nonExistentCode")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("URL not found")));
    }

    @Test
    void getUrlClicks_ownedByAnotherUser_returns403() throws Exception {
        // user1 tries to access user2's link
        mockMvc.perform(get("/api/analytics/url")
                        .header("Authorization", "Bearer " + jwtTokenUser1)
                        .param("shortCode", user2Link1.getShortCode())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Access denied")));
    }

    @Test
    void getUrlClicks_emptyUrlParam_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/url")
                        .header("Authorization", "Bearer " + jwtTokenUser1)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getOverallClicks_withApiKeyAuth_success() throws Exception {
        String rawApiKey = "purl_live_testapikey1234567890abcdef";
        com.preonsurl.apis.apikey.ApiKey apiKey = new com.preonsurl.apis.apikey.ApiKey();
        apiKey.setUserId(user1.getId());
        apiKey.setName("Analytics Key");
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawApiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            apiKey.setApiKeyHash(java.util.HexFormat.of().formatHex(hash));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        apiKey.setActive(true);
        apiKeyRepository.save(apiKey);

        LocalDate today = LocalDate.now();
        createAccessLog(user1Link1, today.atTime(12, 0));

        mockMvc.perform(get("/analytics/overall")
                        .header("X-API-KEY", rawApiKey)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalClicks").value(1));
    }
}

