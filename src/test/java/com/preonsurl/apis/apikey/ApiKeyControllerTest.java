package com.preonsurl.apis.apikey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.auth.JwtService;
import com.preonsurl.apis.auth.cache.UserCache;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private APIKeyCache apiKeyCache;

    @Autowired
    private UserCache userCache;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private NewUrlRepository shortUrlRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        apiKeyCache.clear();
        userCache.clear();
        apiKeyRepository.deleteAll();
        shortUrlRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = tenantRepository.save(new Tenant("Acme Corp"));
        testUser = userRepository.save(new User(tenant.getId(), "John Doe", "johndoe", passwordEncoder.encode("Secret123!")));
        jwtToken = jwtService.generateToken(testUser);
    }

    @Test
    void unauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(post("/api/apikey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"My Key\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/apikey"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/apikey"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removedKeysEndpointReturnsNotFoundOrMethodNotAllowed() throws Exception {
        // Verify that old /api/keys route is removed from ApiKeyController
        mockMvc.perform(get("/api/keys")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/keys")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Old Key\"}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/keys")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void createApiKeyWithJwtGeneratesActiveKey() throws Exception {
        String requestJson = """
                {
                    "name": "Production Key"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Production Key"))
                .andExpect(jsonPath("$.data.apiKey").value(startsWith("pk_")))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String rawKey = responseNode.get("data").get("apiKey").asText();
        assertNotNull(rawKey);

        // Verify key works to create short URLs
        String linkCreatePayload = """
                {
                    "url": "https://example.com/live-service"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkCreatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void singleActiveKeyInvariantEnforcedWhenCreatingNewKey() throws Exception {
        // 1. Create first API key
        MvcResult firstResult = mockMvc.perform(post("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"First Key\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String firstRawKey = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .get("data").get("apiKey").asText();

        // Ensure first key works
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", firstRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/first-call\"}"))
                .andExpect(status().isOk());

        // 2. Create second API key (rotates/replaces active key)
        MvcResult secondResult = mockMvc.perform(post("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Second Key\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String secondRawKey = objectMapper.readTree(secondResult.getResponse().getContentAsString())
                .get("data").get("apiKey").asText();

        assertNotEquals(firstRawKey, secondRawKey);

        // 3. Invariant check in DB: Only 1 active key must exist for this user
        List<ApiKey> activeKeys = apiKeyRepository.findAllByUserIdAndActiveTrue(testUser.getId());
        assertEquals(1, activeKeys.size(), "There must be strictly 1 active API key for the user");
        assertEquals("Second Key", activeKeys.get(0).getName());

        List<ApiKey> allKeys = apiKeyRepository.findAllByUserId(testUser.getId());
        assertEquals(2, allKeys.size());

        // 4. First key must now be rejected (401 Unauthorized)
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", firstRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/first-retry\"}"))
                .andExpect(status().isUnauthorized());

        // 5. Second key must work
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", secondRawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/second-call\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getActiveApiKeyReturnsCurrentKeyMetadata() throws Exception {
        // Initially no active key
        mockMvc.perform(get("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());

        // Create key
        mockMvc.perform(post("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"My Active Key\"}"))
                .andExpect(status().isCreated());

        // Fetch active key
        mockMvc.perform(get("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("My Active Key"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void deleteActiveApiKeyDeactivatesAndInvalidatesAccess() throws Exception {
        // Create key
        MvcResult createResult = mockMvc.perform(post("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"To Delete\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String rawKey = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("apiKey").asText();

        // Key works initially
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/before-delete\"}"))
                .andExpect(status().isOk());

        // Delete active key
        mockMvc.perform(delete("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Active API key deleted successfully"));

        // In DB: active key list is empty
        List<ApiKey> activeKeys = apiKeyRepository.findAllByUserIdAndActiveTrue(testUser.getId());
        assertTrue(activeKeys.isEmpty());

        // GET /api/apikey returns 404
        mockMvc.perform(get("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());

        // Subsequent /link/create with that key fails with 401
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/after-delete\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteApiKeyByIdDeactivatesKey() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/apikey")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Delete By Id\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        long keyId = data.get("id").asLong();
        String rawKey = data.get("apiKey").asText();

        // Delete by ID
        mockMvc.perform(delete("/api/apikey/" + keyId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());

        // Key is now deactivated
        ApiKey key = apiKeyRepository.findById(keyId).orElseThrow();
        assertFalse(key.isActive());

        // Using key returns 401
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/test-after-id-delete\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteApiKeyOfAnotherUserFailsWith404() throws Exception {
        // Create another tenant and user
        Tenant anotherTenant = tenantRepository.save(new Tenant("Another Corp"));
        User anotherUser = userRepository.save(new User(anotherTenant.getId(), "Alice", "alice", "pass"));
        String anotherJwt = jwtService.generateToken(anotherUser);

        // Another user creates an API key
        MvcResult createResult = mockMvc.perform(post("/api/apikey")
                        .header("Authorization", "Bearer " + anotherJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Alice's Key\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        long aliceKeyId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // First user tries to delete Alice's key by ID -> should return 404
        mockMvc.perform(delete("/api/apikey/" + aliceKeyId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());

        // Alice's key is still active
        ApiKey aliceKey = apiKeyRepository.findById(aliceKeyId).orElseThrow();
        assertTrue(aliceKey.isActive());
    }
}
