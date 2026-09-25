package com.preonsurl.apis.publiclink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.publiclink.cache.PublicSecureUrlLruCache;
import com.preonsurl.apis.publiclink.entity.PublicSecureUrl;
import com.preonsurl.apis.publiclink.entity.PublicSecureUrlAccessLog;
import com.preonsurl.apis.publiclink.ratelimit.PublicLinkRateLimiter;
import com.preonsurl.apis.publiclink.repository.PublicSecureUrlAccessLogRepository;
import com.preonsurl.apis.publiclink.repository.PublicSecureUrlRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicLinkIntegrationTest {
//
//    private static final String TARGET_URL =
//            "https://www.perniaspopupshop.com/";
//
//    private static final String PRIVATE_PIN = "111111";
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private PublicSecureUrlRepository publicSecureUrlRepository;
//
//    @Autowired
//    private PublicSecureUrlAccessLogRepository accessLogRepository;
//
//    @Autowired
//    private PublicLinkRateLimiter rateLimiter;
//
//    @Autowired
//    private PublicSecureUrlLruCache lruCache;
//
//    private final ObjectMapper objectMapper =
//            new ObjectMapper().findAndRegisterModules();
//
//    @BeforeEach
//    void setUp() {
//        rateLimiter.clear();
//        lruCache.clear();
//    }
//
//    @Test
//    void createPublicLink_public_success() throws Exception {
//
//        String payload = """
//                {
//                  "url": "%s",
//                  "linkMode": "REDIRECT",
//                  "addShortCode": true,
//                  "accessPolicies": {
//                    "mode": "PUBLIC",
//                    "public": true,
//                    "secured": false
//                  }
//                }
//                """.formatted(TARGET_URL);
//
//        MvcResult result = mockMvc.perform(
//                        post("/api/public-links")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .header(
//                                        "X-Forwarded-For",
//                                        "203.0.113.1"
//                                )
//                                .header(
//                                        "Host",
//                                        "app.domain.com:8081"
//                                )
//                                .content(payload)
//                )
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success").value(true))
//                .andExpect(jsonPath("$.data.psecureUrl").isNotEmpty())
//                .andExpect(jsonPath("$.data.linkMode")
//                        .value("REDIRECT"))
//                .andExpect(jsonPath("$.data.createdAt")
//                        .isNotEmpty())
//                .andExpect(jsonPath("$.data.processingNs")
//                        .isNumber())
//                .andReturn();
//
//        JsonNode data = objectMapper
//                .readTree(result.getResponse().getContentAsString())
//                .path("data");
//
//        String shortCode = extractShortCode(
//                data.path("psecureUrl").asText()
//        );
//
//        assertThat(data.path("psecureUrl").asText())
//                .contains("psecure")
//                .endsWith("/" + shortCode);
//
//        assertThat(data.path("processingNs").asLong())
//                .isGreaterThan(0);
//
//        // Async persistence
//        waitForPersistence(shortCode);
//
//        Optional<PublicSecureUrl> entityOpt =
//                publicSecureUrlRepository.findByShortKey(shortCode);
//
//        assertThat(entityOpt).isPresent();
//
//        PublicSecureUrl entity = entityOpt.get();
//
//        assertThat(entity.getOriginalUrl())
//                .isEqualTo(TARGET_URL);
//
//        assertThat(entity.getPin())
//                .isNull();
//
//        assertThat(entity.getMode())
//                .isEqualTo("PUBLIC");
//
//        assertThat(entity.getIpAddress())
//                .isEqualTo("203.0.113.1");
//    }
//
//    @Test
//    void createPublicLink_privatePin_success() throws Exception {
//
//        String payload = """
//                {
//                  "url": "%s",
//                  "linkMode": "REDIRECT",
//                  "addShortCode": true,
//                  "accessPolicies": {
//                    "mode": "SECURED",
//                    "pin": {
//                      "pin": "%s"
//                    },
//                    "secured": true,
//                    "public": false
//                  }
//                }
//                """.formatted(TARGET_URL, PRIVATE_PIN);
//
//        MvcResult result = mockMvc.perform(
//                        post("/api/public-links")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .header(
//                                        "X-Forwarded-For",
//                                        "203.0.113.2"
//                                )
//                                .content(payload)
//                )
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success").value(true))
//                .andExpect(jsonPath("$.data.psecureUrl")
//                        .isNotEmpty())
//                .andExpect(jsonPath("$.data.linkMode")
//                        .value("REDIRECT"))
//                .andExpect(jsonPath("$.data.processingNs")
//                        .isNumber())
//                .andReturn();
//
//        JsonNode data = objectMapper
//                .readTree(result.getResponse().getContentAsString())
//                .path("data");
//
//        String shortCode = extractShortCode(
//                data.path("psecureUrl").asText()
//        );
//
//        assertThat(data.path("processingNs").asLong())
//                .isGreaterThan(0);
//
//        waitForPersistence(shortCode);
//
//        Optional<PublicSecureUrl> entityOpt =
//                publicSecureUrlRepository.findByShortKey(shortCode);
//
//        assertThat(entityOpt).isPresent();
//
//        PublicSecureUrl entity = entityOpt.get();
//
//        assertThat(entity.getOriginalUrl())
//                .isEqualTo(TARGET_URL);
//
//        assertThat(entity.getPin())
//                .isEqualTo(PRIVATE_PIN);
//
//        assertThat(entity.getMode())
//                .isEqualTo("SECURED");
//
//        assertThat(entity.getIpAddress())
//                .isEqualTo("203.0.113.2");
//    }
//
//    @Test
//    void createPublicLink_rateLimit_oneRequestPerTwoSeconds()
//            throws Exception {
//
//        String payload = """
//                {
//                  "url": "%s",
//                  "linkMode": "REDIRECT"
//                }
//                """.formatted(TARGET_URL);
//
//        String clientIp = "198.51.100.99";
//
//        // First request succeeds.
//        mockMvc.perform(
//                        post("/api/public-links")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .header(
//                                        "X-Forwarded-For",
//                                        clientIp
//                                )
//                                .content(payload)
//                )
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success")
//                        .value(true));
//
//        // Immediate second request is rate limited.
//        mockMvc.perform(
//                        post("/api/public-links")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .header(
//                                        "X-Forwarded-For",
//                                        clientIp
//                                )
//                                .content(payload)
//                )
//                .andExpect(status().isTooManyRequests())
//                .andExpect(header().string(
//                        "Retry-After",
//                        "2"
//                ))
//                .andExpect(jsonPath("$.success")
//                        .value(false))
//                .andExpect(jsonPath("$.message")
//                        .value(
//                                "Rate limit exceeded. Try After sometime"
//                        ));
//
//        // Different IP is not rate limited.
//        mockMvc.perform(
//                        post("/api/public-links")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .header(
//                                        "X-Forwarded-For",
//                                        "198.51.100.100"
//                                )
//                                .content(payload)
//                )
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success")
//                        .value(true));
//    }
//
//    @Test
//    void servePrivateLink_pinChallenge_wrongPin_correctPin()
//            throws Exception {
//
//        String payload = """
//                {
//                  "url": "%s",
//                  "linkMode": "REDIRECT",
//                  "accessPolicies": {
//                    "mode": "SECURED",
//                    "pin": {
//                      "pin": "%s"
//                    },
//                    "secured": true,
//                    "public": false
//                  }
//                }
//                """.formatted(TARGET_URL, PRIVATE_PIN);
//
//        MvcResult createResult = mockMvc.perform(
//                        post("/api/public-links")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .header(
//                                        "X-Forwarded-For",
//                                        "203.0.113.50"
//                                )
//                                .content(payload)
//                )
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success")
//                        .value(true))
//                .andReturn();
//
//        JsonNode data = objectMapper
//                .readTree(createResult.getResponse()
//                        .getContentAsString())
//                .path("data");
//
//        String shortCode = extractShortCode(
//                data.path("psecureUrl").asText()
//        );
//
//        /*
//         * Wait until the asynchronous persistence has completed.
//         */
//        waitForPersistence(shortCode);
//
//        /*
//         * 1. First request should show PIN challenge.
//         */
//        MvcResult challengeResult = mockMvc.perform(
//                        get("/psecure/" + shortCode)
//                )
//                .andExpect(status().isOk())
//                .andReturn();
//
//        String challengeHtml =
//                challengeResult.getResponse()
//                        .getContentAsString();
//
//        assertThat(challengeHtml)
//                .contains("Enter PIN to Continue");
//
//        /*
//         * 2. Wrong PIN must be rejected.
//         */
//        mockMvc.perform(
//                        post("/psecure/" + shortCode + "/verify")
//                                .param("pin", "999999")
//                )
//                .andExpect(status().isUnauthorized())
//                .andExpect(result ->
//                        assertThat(
//                                result.getResponse()
//                                        .getContentAsString()
//                        ).contains("Invalid PIN entered")
//                );
//
//        /*
//         * 3. Correct PIN must redirect.
//         */
//        MvcResult verifiedResult = mockMvc.perform(
//                        post("/psecure/" + shortCode + "/verify")
//                                .param("pin", PRIVATE_PIN)
//                                .header(
//                                        "User-Agent",
//                                        "PublicLinkIntegrationTest/1.0"
//                                )
//                )
//                .andExpect(status().isFound())
//                .andExpect(header().string(
//                        "Location",
//                        TARGET_URL
//                ))
//                .andReturn();
//
//        /*
//         * 4. Verification cookie must be created.
//         */
//        String setCookie = verifiedResult
//                .getResponse()
//                .getHeader("Set-Cookie");
//
//        assertThat(setCookie)
//                .isNotNull();
//
//        assertThat(setCookie)
//                .contains(
//                        "PREONS_PSEC_" + shortCode + "=VERIFIED"
//                );
//
//        /*
//         * 5. Verified cookie should bypass PIN challenge.
//         */
//        mockMvc.perform(
//                        get("/psecure/" + shortCode)
//                                .cookie(
//                                        new Cookie(
//                                                "PREONS_PSEC_" + shortCode,
//                                                "VERIFIED"
//                                        )
//                                )
//                )
//                .andExpect(status().isFound())
//                .andExpect(header().string(
//                        "Location",
//                        TARGET_URL
//                ));
//
//        /*
//         * 6. Access logs should have been generated.
//         */
//        waitForAccessLogs(shortCode);
//
//        List<PublicSecureUrlAccessLog> logs =
//                accessLogRepository.findByShortKey(shortCode);
//
//        assertThat(logs)
//                .isNotEmpty();
//    }
//
//    @Test
//    void servePublicLink_shouldRedirectWithoutPin()
//            throws Exception {
//
//        String payload = """
//                {
//                  "url": "%s",
//                  "linkMode": "REDIRECT",
//                  "accessPolicies": {
//                    "mode": "PUBLIC",
//                    "public": true,
//                    "secured": false
//                  }
//                }
//                """.formatted(TARGET_URL);
//
//        MvcResult createResult = mockMvc.perform(
//                        post("/api/public-links")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .header(
//                                        "X-Forwarded-For",
//                                        "203.0.113.60"
//                                )
//                                .content(payload)
//                )
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success")
//                        .value(true))
//                .andReturn();
//
//        JsonNode data = objectMapper
//                .readTree(createResult.getResponse()
//                        .getContentAsString())
//                .path("data");
//
//        String shortCode = extractShortCode(
//                data.path("psecureUrl").asText()
//        );
//
//        waitForPersistence(shortCode);
//
//        /*
//         * Public links should redirect immediately.
//         */
//        mockMvc.perform(
//                        get("/psecure/" + shortCode)
//                )
//                .andExpect(status().isFound())
//                .andExpect(header().string(
//                        "Location",
//                        TARGET_URL
//                ));
//    }
//
//    private String extractShortCode(String psecureUrl) {
//
//        assertThat(psecureUrl)
//                .isNotBlank();
//
//        int lastSlash = psecureUrl.lastIndexOf('/');
//
//        assertThat(lastSlash)
//                .isGreaterThan(0);
//
//        return psecureUrl.substring(lastSlash + 1);
//    }
//
//    private void waitForPersistence(String shortCode)
//            throws InterruptedException {
//
//        for (int i = 0; i < 20; i++) {
//
//            if (publicSecureUrlRepository
//                    .findByShortKey(shortCode)
//                    .isPresent()) {
//                return;
//            }
//
//            Thread.sleep(50);
//        }
//
//        assertThat(
//                publicSecureUrlRepository
//                        .findByShortKey(shortCode)
//        ).isPresent();
//    }
//
//    private void waitForAccessLogs(String shortCode)
//            throws InterruptedException {
//
//        for (int i = 0; i < 20; i++) {
//
//            List<PublicSecureUrlAccessLog> logs =
//                    accessLogRepository.findByShortKey(shortCode);
//
//            if (!logs.isEmpty()) {
//                return;
//            }
//
//            Thread.sleep(50);
//        }
//
//        assertThat(
//                accessLogRepository.findByShortKey(shortCode)
//        ).isNotEmpty();
//    }
}