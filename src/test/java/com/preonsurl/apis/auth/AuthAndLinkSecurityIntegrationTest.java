package com.preonsurl.apis.auth;

import com.preonsurl.apis.apikey.ApiKey;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.apikey.ApiKeyRepository;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private NewUrlRepository shortUrlRepository;

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
                "email": "alice@example.com",
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
        assertEquals("alice@example.com", user.getEmail());
        assertTrue(passwordEncoder.matches("Password123!", user.getPasswordHash()));
    }

    @Test
    void registerWithDuplicateUsernameReturns400BadRequest() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Existing Tenant"));

        User existingUser = new User(
                tenant.getId(),
                "Bob Smith",
                "bob",
                passwordEncoder.encode("Secret!")
        );
        existingUser.setEmail("bob@example.com");

        userRepository.save(existingUser);

        String payload = """
            {
                "fullName": "Another Bob",
                "username": "bob",
                "email": "anotherbob@example.com",
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

    @Test
    void corsPreflightRequestToLoginFromOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://127.0.0.1:5500")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5500"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void corsLoginPostRequestIncludesCorsHeaders() throws Exception {
        String payload = """
                {
                    "username": "user",
                    "password": "password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://127.0.0.1:5500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5500"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // =========================================================================
    // Verification & Emailer Tests
    // =========================================================================

    @Test
    void registerUserCreatesUnverifiedAccountWith6DigitCode() throws Exception {
        String payload = """
                {
                    "fullName": "George Washington",
                    "username": "george",
                    "email": "george@mountvernon.org",
                    "password": "PresidentPass2026!"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("george"))
                .andExpect(jsonPath("$.data.email").value("george@mountvernon.org"))
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.requiresVerification").value(true));

        User user = userRepository.findByUsername("george").orElseThrow();
        assertFalse(user.isVerified());
        assertNotNull(user.getVerificationCode());
        assertEquals(6, user.getVerificationCode().length());
        assertTrue(user.getVerificationCode().matches("^[0-9]{6}$"));
    }

    @Test
    void verifyEmailWithValidCodeActivatesUser() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Virginia Corp"));
        User user = new User(
                tenant.getId(),
                "Thomas Jefferson",
                "thomas",
                passwordEncoder.encode("Pass1234!"),
                "thomas@monticello.org",
                false,
                "123456",
                java.time.Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES)
        );
        userRepository.save(user);

        String verifyPayload = """
                {
                    "email": "thomas@monticello.org",
                    "code": "123456"
                }
                """;

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));

        User updated = userRepository.findByUsername("thomas").orElseThrow();
        assertTrue(updated.isVerified());
        assertNull(updated.getVerificationCode());
    }

    @Test
    void verifyEmailWithInvalidCodeReturnsBadRequest() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Virginia Corp"));
        User user = new User(
                tenant.getId(),
                "James Madison",
                "james",
                passwordEncoder.encode("Pass1234!"),
                "james@constitution.org",
                false,
                "123456",
                java.time.Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES)
        );
        userRepository.save(user);

        String verifyPayload = """
                {
                    "email": "james@constitution.org",
                    "code": "999999"
                }
                """;

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        User unchanged = userRepository.findByUsername("james").orElseThrow();
        assertFalse(unchanged.isVerified());
    }

    @Test
    void resendVerificationCodeGeneratesNewCode() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Virginia Corp"));
        User user = new User(
                tenant.getId(),
                "James Monroe",
                "monroe",
                passwordEncoder.encode("Pass1234!"),
                "monroe@whitehouse.gov",
                false,
                "111111",
                java.time.Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES)
        );
        userRepository.save(user);

        String resendPayload = """
                {
                    "email": "monroe@whitehouse.gov"
                }
                """;

        mockMvc.perform(post("/api/auth/resend-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resendPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updated = userRepository.findByUsername("monroe").orElseThrow();
        assertNotNull(updated.getVerificationCode());
        assertEquals(6, updated.getVerificationCode().length());
    }

    @Test
    void changeEmailUpdatesEmailAndGeneratesFreshCode() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Treasury Corp"));
        User user = new User(
                tenant.getId(),
                "Alexander Hamilton",
                "hamilton",
                passwordEncoder.encode("Pass1234!"),
                "old.hamilton@treasury.gov",
                false,
                "222222",
                java.time.Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES)
        );
        userRepository.save(user);

        String changePayload = """
                {
                    "currentIdentifier": "old.hamilton@treasury.gov",
                    "newEmail": "new.hamilton@treasury.gov"
                }
                """;

        mockMvc.perform(post("/api/auth/change-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updated = userRepository.findByUsername("hamilton").orElseThrow();
        assertEquals("new.hamilton@treasury.gov", updated.getEmail());
        assertFalse(updated.isVerified());
        assertNotNull(updated.getVerificationCode());
    }

    @Test
    void loginUnverifiedUserReturnsUnverifiedResponse() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Unverified Corp"));
        User user = new User(
                tenant.getId(),
                "Pending User",
                "pending",
                passwordEncoder.encode("SecretPass123!"),
                "pending@example.com",
                false,
                "555555",
                java.time.Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES)
        );
        userRepository.save(user);

        String loginPayload = """
                {
                    "username": "pending",
                    "password": "SecretPass123!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.email").value("pending@example.com"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }
}

