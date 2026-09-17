package com.preonsurl.apis;

import com.preonsurl.apis.entity.ShortUrl;
import com.preonsurl.apis.entity.ShortUrlAccessLog;
import com.preonsurl.apis.repository.ShortUrlAccessLogRepository;
import com.preonsurl.apis.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ServingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private ShortUrlAccessLogRepository accessLogRepository;

    @BeforeEach
    void setUp() {
        accessLogRepository.deleteAll();
        shortUrlRepository.deleteAll();
    }

    @Test
    void rootEndpointReturnsApiStatus() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("your-shortner"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void servingRootShortCodeRedirectsToOriginalUrlAndLogsAccess() throws Exception {
        ShortUrl shortUrl = new ShortUrl(
                "abcXYZ1",
                "https://example.com/target-page",
                null,
                "http://localhost:8081/abcXYZ1"
        );
        ShortUrl saved = shortUrlRepository.save(shortUrl);

        mockMvc.perform(get("/abcXYZ1")
                        .header("X-Forwarded-For", "203.0.113.195")
                        .header("User-Agent", "PreonsBrowser/1.0")
                        .header("Referer", "https://google.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target-page"));

        ShortUrl updated = shortUrlRepository.findByShortCode("abcXYZ1").orElseThrow();
        assertEquals(1, updated.getClickCount(), "Click count should be incremented to 1");

        List<ShortUrlAccessLog> logs = accessLogRepository.findByShortCodeOrderByAccessedAtDesc("abcXYZ1");
        assertEquals(1, logs.size(), "One access log record should be created");
        ShortUrlAccessLog log = logs.get(0);
        assertEquals(saved.getId(), log.getShortUrlId());
        assertEquals("abcXYZ1", log.getShortCode());
        assertEquals("203.0.113.195", log.getIpAddress());
        assertEquals("PreonsBrowser/1.0", log.getUserAgent());
        assertEquals("https://google.com", log.getReferer());
    }

    @Test
    void servingDirectoryShortCodeRedirectsToOriginalUrlAndLogsAccess() throws Exception {
        ShortUrl shortUrl = new ShortUrl(
                "inv1234",
                "https://example.com/billing/invoice/456",
                "invoice",
                "http://localhost:8081/invoice/inv1234"
        );
        ShortUrl saved = shortUrlRepository.save(shortUrl);

        mockMvc.perform(get("/invoice/inv1234")
                        .header("User-Agent", "TestAgent"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/billing/invoice/456"));

        ShortUrl updated = shortUrlRepository.findByShortCode("inv1234").orElseThrow();
        assertEquals(1, updated.getClickCount(), "Click count should be incremented to 1");

        List<ShortUrlAccessLog> logs = accessLogRepository.findByShortUrlIdOrderByAccessedAtDesc(saved.getId());
        assertEquals(1, logs.size(), "Access log should be saved for directory short code");
        assertEquals("inv1234", logs.get(0).getShortCode());
        assertEquals("TestAgent", logs.get(0).getUserAgent());
    }

    @Test
    void servingNonExistentShortCodeReturns404NotFoundAndDoesNotLog() throws Exception {
        mockMvc.perform(get("/nonexistent123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Short URL not found"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for 404");
    }

    @Test
    void servingNonExistentDirectoryShortCodeReturns404NotFoundAndDoesNotLog() throws Exception {
        mockMvc.perform(get("/invoice/unknown999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Short URL not found"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for 404");
    }

    @Test
    void servingExpiredRootShortCodeReturns410GoneAndDoesNotLog() throws Exception {
        ShortUrl shortUrl = new ShortUrl(
                "expRoot",
                "https://example.com/expired-root",
                null,
                "http://localhost:8081/expRoot",
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        shortUrlRepository.save(shortUrl);

        mockMvc.perform(get("/expRoot"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Short URL has expired"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for expired URL");
    }

    @Test
    void servingExpiredDirectoryShortCodeReturns410GoneAndDoesNotLog() throws Exception {
        ShortUrl shortUrl = new ShortUrl(
                "expDir",
                "https://example.com/expired-dir",
                "promo",
                "http://localhost:8081/promo/expDir",
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        shortUrlRepository.save(shortUrl);

        mockMvc.perform(get("/promo/expDir"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Short URL has expired"));

        assertEquals(0, accessLogRepository.count(), "No access log should be recorded for expired URL");
    }

    @Test
    void servingShortCodeWithUsageLimitOnceSucceedsFirstTimeAndFailsSecondTime() throws Exception {
        ShortUrl shortUrl = new ShortUrl(
                "onceCode",
                "https://example.com/one-time",
                null,
                "http://localhost:8081/onceCode",
                Instant.now().plus(1, ChronoUnit.DAYS),
                1L
        );
        shortUrlRepository.save(shortUrl);

        // 1st request -> Succeeds and redirects
        mockMvc.perform(get("/onceCode"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/one-time"));

        ShortUrl afterFirst = shortUrlRepository.findByShortCode("onceCode").orElseThrow();
        assertEquals(1, afterFirst.getClickCount(), "Usage should be incremented to 1");
        assertEquals(1, accessLogRepository.count(), "One access log should be recorded");

        // 2nd request -> Exceeded limit -> 410 Gone
        mockMvc.perform(get("/onceCode"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Short URL usage limit reached"));

        ShortUrl afterSecond = shortUrlRepository.findByShortCode("onceCode").orElseThrow();
        assertEquals(1, afterSecond.getClickCount(), "Usage should still be 1 (not incremented on exceeded)");
        assertEquals(1, accessLogRepository.count(), "Access log should not be recorded on exceeded");
    }

    @Test
    void servingDirectoryShortCodeWithUsageLimitEnforced() throws Exception {
        ShortUrl shortUrl = new ShortUrl(
                "twoCode",
                "https://example.com/twice",
                "deals",
                "http://localhost:8081/deals/twoCode",
                Instant.now().plus(1, ChronoUnit.DAYS),
                2L
        );
        shortUrlRepository.save(shortUrl);

        // 1st request -> OK
        mockMvc.perform(get("/deals/twoCode"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/twice"));

        // 2nd request -> OK
        mockMvc.perform(get("/deals/twoCode"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/twice"));

        ShortUrl afterSecond = shortUrlRepository.findByDirTypeAndShortCode("deals", "twoCode").orElseThrow();
        assertEquals(2, afterSecond.getClickCount(), "Usage should be incremented to 2");
        assertEquals(2, accessLogRepository.count());

        // 3rd request -> 410 Gone
        mockMvc.perform(get("/deals/twoCode"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Short URL usage limit reached"));

        ShortUrl afterThird = shortUrlRepository.findByDirTypeAndShortCode("deals", "twoCode").orElseThrow();
        assertEquals(2, afterThird.getClickCount(), "Usage should not be incremented after reaching limit");
        assertEquals(2, accessLogRepository.count());
    }

    @Test
    void servingShortCodeWithUnlimitedUsageCanBeAccessedMultipleTimes() throws Exception {
        ShortUrl shortUrl = new ShortUrl(
                "unlimitedCode",
                "https://example.com/unlimited",
                null,
                "http://localhost:8081/unlimitedCode",
                Instant.now().plus(1, ChronoUnit.DAYS),
                null
        );
        shortUrlRepository.save(shortUrl);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/unlimitedCode"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://example.com/unlimited"));
        }

        ShortUrl afterFive = shortUrlRepository.findByShortCode("unlimitedCode").orElseThrow();
        assertEquals(5, afterFive.getClickCount(), "Usage should be incremented to 5");
        assertEquals(5, accessLogRepository.count());
    }
}
