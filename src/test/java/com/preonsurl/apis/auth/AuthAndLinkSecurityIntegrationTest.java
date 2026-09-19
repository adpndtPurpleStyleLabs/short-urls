package com.preonsurl.apis.auth;

import com.preonsurl.apis.apikey.ApiKey;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.apikey.ApiKeyRepository;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthAndLinkSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        apiKeyRepository.deleteAll();
        shortUrlRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // Registration Tests
    // =========================================================================

    @Test
    void registerNewUserSucceeds() throws Exception {
        String payload = """
                {
                    "fullName": "Alice Johnson",
                    "username": "alice",
                    "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("alice"));

        assertTrue(userRepository.findByUsername("alice").isPresent());
        User user = userRepository.findByUsername("alice").get();
        assertEquals("Alice Johnson", user.getFullName());
        assertTrue(passwordEncoder.matches("Password123!", user.getPasswordHash()));
    }

    @Test
    void registerWithDuplicateUsernameReturns400BadRequest() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Existing Tenant"));
        userRepository.save(new User(tenant.getId(), "Bob Smith", "bob", passwordEncoder.encode("Secret!")));

        String payload = """
                {
                    "fullName": "Another Bob",
                    "username": "bob",
                    "password": "NewPassword123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // Login Tests
    // =========================================================================

    @Test
    void loginWithValidCredentialsReturnsJwtToken() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Test Corp"));
        userRepository.save(new User(tenant.getId(), "Charlie Brown", "charlie", passwordEncoder.encode("CorrectPassword!")));

        String loginPayload = """
                {
                    "username": "charlie",
                    "password": "CorrectPassword!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value(notNullValue()))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void loginWithIncorrectPasswordReturns401Unauthorized() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Test Corp"));
        userRepository.save(new User(tenant.getId(), "Dave Davis", "dave", passwordEncoder.encode("CorrectPassword!")));

        String loginPayload = """
                {
                    "username": "dave",
                    "password": "WrongPassword!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginWithNonExistentUserReturns401Unauthorized() throws Exception {
        String loginPayload = """
                {
                    "username": "unknown_user",
                    "password": "any_password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // /link/create: API Key vs JWT Authentication Tests
    // =========================================================================

    @Test
    void createLinkWithActiveApiKeySucceeds() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Key Tenant"));
        User user = userRepository.save(new User(tenant.getId(), "Dave Grohl", "dave", passwordEncoder.encode("Pass!")));

        String plainApiKey = "preons_active_key_112233";
        ApiKey apiKeyEntity = new ApiKey();
        apiKeyEntity.setUserId(user.getId());
        apiKeyEntity.setApiKeyHash(sha256(plainApiKey));
        apiKeyEntity.setName("Dave's Key");
        apiKeyEntity.setActive(true);
        apiKeyRepository.save(apiKeyEntity);

        String payload = """
                {
                    "url": "https://example.com/api-key-test"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", plainApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/api-key-test"));
    }

    @Test
    void createLinkWithDatabaseApiKeySucceeds() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Acme"));
        User user = userRepository.save(new User(tenant.getId(), "Emma Watson", "emma", passwordEncoder.encode("Pass!")));

        String plainApiKey = "preons_db_key_987654321";
        ApiKey apiKeyEntity = new ApiKey();
        apiKeyEntity.setUserId(user.getId());
        apiKeyEntity.setApiKeyHash(sha256(plainApiKey));
        apiKeyEntity.setName("Emma's Prod Key");
        apiKeyEntity.setActive(true);
        apiKeyRepository.save(apiKeyEntity);

        String payload = """
                {
                    "url": "https://example.com/db-key-test"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", plainApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/db-key-test"));
    }

    @Test
    void createLinkWithJwtTokenWhenApiKeyAbsentSucceeds() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Jwt Tenant"));
        User user = userRepository.save(new User(tenant.getId(), "Frank Miller", "frank", passwordEncoder.encode("Pass!")));

        String jwtToken = jwtService.generateToken(user);

        String payload = """
                {
                    "url": "https://example.com/jwt-test"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/jwt-test"));
    }

    @Test
    void createLinkWhenBothApiKeyAndJwtPresentUsesApiKey() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Dual Tenant"));
        User user = userRepository.save(new User(tenant.getId(), "Grace Hopper", "grace", passwordEncoder.encode("Pass!")));
        String jwtToken = jwtService.generateToken(user);

        String plainApiKey = "preons_dual_key_556677";
        ApiKey apiKeyEntity = new ApiKey();
        apiKeyEntity.setUserId(user.getId());
        apiKeyEntity.setApiKeyHash(sha256(plainApiKey));
        apiKeyEntity.setName("Dual Key");
        apiKeyEntity.setActive(true);
        apiKeyRepository.save(apiKeyEntity);

        String payload = """
                {
                    "url": "https://example.com/both-auth-test"
                }
                """;

        // Provide both valid API key and valid JWT: API key is present, so API key is used
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", plainApiKey)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createLinkWhenInvalidApiKeyProvidedWithValidJwtFailsWith401() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Fallback Tenant"));
        User user = userRepository.save(new User(tenant.getId(), "Hank Pym", "hank", passwordEncoder.encode("Pass!")));
        String jwtToken = jwtService.generateToken(user);

        String payload = """
                {
                    "url": "https://example.com/invalid-key-valid-jwt"
                }
                """;

        // Requirement: "if api-key is present then use it if not then if jwt then use jwt"
        // An API key IS present, but it's invalid. Therefore it uses API key and fails (does not fall back to JWT).
        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", "completely-invalid-key")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createLinkWithInvalidApiKeyAloneReturns401Unauthorized() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/invalid-key-alone"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("X-API-KEY", "bad-key-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createLinkWithInvalidJwtAloneReturns401Unauthorized() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/invalid-jwt-alone"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .header("Authorization", "Bearer invalid.jwt.token.value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createLinkWithoutAnyAuthReturns401Unauthorized() throws Exception {
        String payload = """
                {
                    "url": "https://example.com/no-auth"
                }
                """;

        mockMvc.perform(post("/link/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }
}
