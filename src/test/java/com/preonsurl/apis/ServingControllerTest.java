package com.preonsurl.apis;

import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.service.NewUrlServingCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ServingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewUrlRepository shortUrlRepository;

    @Autowired
    private NewUrlAccessLogRepository accessLogRepository;

    @Autowired
    private NewUrlServingCacheService servingCacheService;

    @BeforeEach
    void setUp() {
        servingCacheService.getLruCache().clear();
        accessLogRepository.deleteAll();
        shortUrlRepository.deleteAll();
    }

    @Test
    void rootEndpointReturnsApiStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("007"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void servingRootShortCodeRedirectsToOriginalUrlAndLogsAccess() throws Exception {
        NewUrl newUrl = new NewUrl(
                "abcXYZ1",
                "https://example.com/target-page",
                null,
                "http://localhost/abcXYZ1"
        );
        NewUrl saved = shortUrlRepository.save(newUrl);

        mockMvc.perform(get("/abcXYZ1")
                        .header("X-Forwarded-For", "203.0.113.195")
                        .header("User-Agent", "PreonsBrowser/1.0")
                        .header("Referer", "https://google.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target-page"));

        // Verify placed in LRU cache
        assertNotNull(servingCacheService.getLruCache().get("http://localhost/abcXYZ1"), "Should be placed in LRU cache");

        // Verify background event increments click count and logs access
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl updated = shortUrlRepository.findByShortCode("abcXYZ1").orElseThrow();
            assertEquals(1, updated.getClickCount(), "Click count should be incremented to 1");

            List<NewUrlAccessLog> logs = accessLogRepository.findByShortUrlIdOrderByAccessedAtDesc(saved.getId());
            assertEquals(1, logs.size(), "One access log record should be created");
            NewUrlAccessLog log = logs.get(0);
            assertEquals(saved.getId(), log.getShortUrlId());
            assertEquals("http://localhost/abcXYZ1", log.getShortCode());
            assertEquals("203.0.113.195", log.getIpAddress());
            assertEquals("PreonsBrowser/1.0", log.getUserAgent());
            assertEquals("https://google.com", log.getReferer());
        });
    }

    @Test
    void servingDirectoryShortCodeRedirectsToOriginalUrlAndLogsAccess() throws Exception {
        NewUrl newUrl = new NewUrl(
                "inv1234",
                "https://example.com/billing/invoice/456",
                "invoice",
                "http://localhost/invoice/inv1234"
        );
        NewUrl saved = shortUrlRepository.save(newUrl);

        mockMvc.perform(get("/invoice/inv1234")
                        .header("User-Agent", "TestAgent"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/billing/invoice/456"));

        assertNotNull(servingCacheService.getLruCache().get("http://localhost/invoice/inv1234"), "Should be placed in LRU cache");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl updated = shortUrlRepository.findByShortCode("inv1234").orElseThrow();
            assertEquals(1, updated.getClickCount(), "Click count should be incremented to 1");

            List<NewUrlAccessLog> logs = accessLogRepository.findByShortUrlIdOrderByAccessedAtDesc(saved.getId());
            assertEquals(1, logs.size(), "Access log should be saved for directory short code");
            assertEquals("http://localhost/invoice/inv1234", logs.get(0).getShortCode());
            assertEquals("TestAgent", logs.get(0).getUserAgent());
        });
    }

    @Test
    void servingNonExistentShortCodeReturns404NotFoundAndDoesNotLog() throws Exception {
        mockMvc.perform(get("/nonexistent123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL not found"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for 404");
        assertNull(servingCacheService.getLruCache().get("http://localhost/nonexistent123"));
    }

    @Test
    void servingNonExistentDirectoryShortCodeReturns404NotFoundAndDoesNotLog() throws Exception {
        mockMvc.perform(get("/invoice/unknown999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL not found"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for 404");
        assertNull(servingCacheService.getLruCache().get("http://localhost/invoice/unknown999"));
    }

    @Test
    void servingExpiredRootShortCodeReturns410GoneAndDoesNotLog() throws Exception {
        NewUrl newUrl = new NewUrl(
                "expRoot",
                "https://example.com/expired-root",
                null,
                "http://localhost/expRoot",
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        shortUrlRepository.save(newUrl);

        mockMvc.perform(get("/expRoot"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL has expired"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for expired URL");
        assertNull(servingCacheService.getLruCache().get("http://localhost/expRoot"), "Expired URL must not be in LRU");
    }

    @Test
    void servingExpiredDirectoryShortCodeReturns410GoneAndDoesNotLog() throws Exception {
        NewUrl newUrl = new NewUrl(
                "expDir",
                "https://example.com/expired-dir",
                "promo",
                "http://localhost/promo/expDir",
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        shortUrlRepository.save(newUrl);

        mockMvc.perform(get("/promo/expDir"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL has expired"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for expired URL");
        assertNull(servingCacheService.getLruCache().get("http://localhost/promo/expDir"), "Expired URL must not be in LRU");
    }

    @Test
    void servingShortCodeWithUsageLimitOnceSucceedsFirstTimeAndFailsSecondTime() throws Exception {
        NewUrl newUrl = new NewUrl(
                "onceCode",
                "https://example.com/one-time",
                null,
                "http://localhost/onceCode",
                Instant.now().plus(1, ChronoUnit.DAYS),
                1L
        );
        shortUrlRepository.save(newUrl);

        // 1st request -> Succeeds and redirects
        mockMvc.perform(get("/onceCode"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/one-time"));

        // Usage limit reached on 1st serve -> must be evicted from LRU cache
        assertNull(servingCacheService.getLruCache().get("http://localhost/onceCode"), "Breached limit must be evicted from LRU");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl afterFirst = shortUrlRepository.findByShortCode("onceCode").orElseThrow();
            assertEquals(1, afterFirst.getClickCount(), "Usage should be incremented to 1");
            assertEquals(1, accessLogRepository.count(), "One access log should be recorded");
        });

        // 2nd request -> Exceeded limit -> checks in DB and responds as expired / limit reached
        mockMvc.perform(get("/onceCode"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL usage limit reached"));

        NewUrl afterSecond = shortUrlRepository.findByShortCode("onceCode").orElseThrow();
        assertEquals(1, afterSecond.getClickCount(), "Usage should still be 1 (not incremented on exceeded)");
        assertEquals(1, accessLogRepository.count(), "Access log should not be recorded on exceeded");
    }

    @Test
    void servingDirectoryShortCodeWithUsageLimitEnforced() throws Exception {
        NewUrl newUrl = new NewUrl(
                "twoCode",
                "https://example.com/twice",
                "deals",
                "http://localhost/deals/twoCode",
                Instant.now().plus(1, ChronoUnit.DAYS),
                2L
        );
        shortUrlRepository.save(newUrl);

        // 1st request -> OK, in cache
        mockMvc.perform(get("/deals/twoCode"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/twice"));

        // 2nd request -> OK, limit reached, evicted from cache
        mockMvc.perform(get("/deals/twoCode"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/twice"));

        assertNull(servingCacheService.getLruCache().get("http://localhost/deals/twoCode"), "Should be evicted after 2nd serve");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl afterSecond = shortUrlRepository.findByDirTypeAndShortCode("deals", "twoCode").orElseThrow();
            assertEquals(2, afterSecond.getClickCount(), "Usage should be incremented to 2");
            assertEquals(2, accessLogRepository.count());
        });

        // 3rd request -> checks in DB, responds as limit reached
        mockMvc.perform(get("/deals/twoCode"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New URL usage limit reached"));

        NewUrl afterThird = shortUrlRepository.findByDirTypeAndShortCode("deals", "twoCode").orElseThrow();
        assertEquals(2, afterThird.getClickCount(), "Usage should not be incremented after reaching limit");
        assertEquals(2, accessLogRepository.count());
    }

    @Test
    void servingShortCodeWithUnlimitedUsageCanBeAccessedMultipleTimes() throws Exception {
        NewUrl newUrl = new NewUrl(
                "unlimitedCode",
                "https://example.com/unlimited",
                null,
                "http://localhost/unlimitedCode",
                Instant.now().plus(1, ChronoUnit.DAYS),
                null
        );
        shortUrlRepository.save(newUrl);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/unlimitedCode"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://example.com/unlimited"));
        }

        assertNotNull(servingCacheService.getLruCache().get("http://localhost/unlimitedCode"), "Unlimited entry remains in LRU");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            NewUrl afterFive = shortUrlRepository.findByShortCode("unlimitedCode").orElseThrow();
            assertEquals(5, afterFive.getClickCount(), "Usage should be incremented to 5");
            assertEquals(5, accessLogRepository.count());
        });
    }

    @Test
    void servingCustomSlugRedirectsSuccessfully() throws Exception {
        NewUrl slugUrl = new NewUrl(
                "diwali-sale",
                "https://example.com/diwali-destination",
                null,
                "http://localhost/diwali-sale",
                Instant.now().plus(1, ChronoUnit.DAYS),
                null
        );
        shortUrlRepository.save(slugUrl);

        mockMvc.perform(get("/diwali-sale"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/diwali-destination"));

        NewUrl dirSlugUrl = new NewUrl(
                "spring-sale",
                "https://example.com/spring-destination",
                "deals",
                "http://localhost/deals/spring-sale",
                Instant.now().plus(1, ChronoUnit.DAYS),
                null
        );
        shortUrlRepository.save(dirSlugUrl);

        mockMvc.perform(get("/deals/spring-sale"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/spring-destination"));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertEquals(2, accessLogRepository.count(), "Both slug accesses should be logged");
        });
    }
}
