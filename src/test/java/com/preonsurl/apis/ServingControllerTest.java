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
}
