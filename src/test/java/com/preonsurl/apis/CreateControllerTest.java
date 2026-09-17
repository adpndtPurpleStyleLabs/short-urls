package com.preonsurl.apis;

import com.preonsurl.apis.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/products/item1"))
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl", startsWith("http://localhost:8081/")))
                .andExpect(jsonPath("$.existing").value(false));

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
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/invoices/999"))
                .andExpect(jsonPath("$.dirType").value("invoice"))
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl", startsWith("http://localhost:8081/invoice/")))
                .andExpect(jsonPath("$.existing").value(false));

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
                .andExpect(jsonPath("$.existing").value(false));

        // Second creation for same URL and dirType
        mockMvc.perform(post("/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existing").value(true));

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
                .andExpect(jsonPath("$.existing").value(false));
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
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void createWithEmptyUrlReturns400BadRequest() throws Exception {
        mockMvc.perform(post("/create")
                        .header("X-API-KEY", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
