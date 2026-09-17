package com.preonsurl.apis;

import com.preonsurl.apis.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CreateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    private static final String VALID_API_KEY = "test-api-key-12345";
    private static final String INVALID_API_KEY = "wrong-api-key";

    @BeforeEach
    void setUp() {
        shortUrlRepository.deleteAll();
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
}
