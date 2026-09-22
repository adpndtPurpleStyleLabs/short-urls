package com.preonsurl.ui;

import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void getRegisterPage_returns200AndLuxuryHtml() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("PreonsURL")))
                .andExpect(content().string(containsString("Register Now")))
                .andExpect(content().string(containsString("Establish Account")))
                .andExpect(content().string(containsString("Full Name")))
                .andExpect(content().string(containsString("Email Address")))
                .andExpect(content().string(containsString("Master Key / Password")));
    }

    @Test
    void getLoginPage_returns200AndLuxuryHtml() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("PreonsURL")))
                .andExpect(content().string(containsString("Welcome Back")))
                .andExpect(content().string(containsString("Authenticate")))
                .andExpect(content().string(containsString("Username")));
    }

    @Test
    void getLoginPage_withRegisteredTrue_displaysSuccessBanner() throws Exception {
        mockMvc.perform(get("/login").param("registered", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Account registered successfully")));
    }

    @Test
    void getLoginPage_withVerifiedTrue_displaysVerifiedSuccessBanner() throws Exception {
        mockMvc.perform(get("/login").param("verified", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Email verified successfully")));
    }

    @Test
    void postRegister_validData_delegatesToAuthControllerAndRedirectsToVerification() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Alexander Hamilton")
                        .param("username", "ahamilton")
                        .param("email", "alexander@example.com")
                        .param("password", "Treasury2026!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verification?email=alexander%40example.com"));

        // Verify user was registered in DB with isVerified = false
        assertTrue(userRepository.findByUsername("ahamilton").isPresent(), "User should be registered in database");
        User user = userRepository.findByUsername("ahamilton").get();
        assertFalse(user.isVerified(), "User should initially be unverified");
        assertTrue(user.getVerificationCode() != null && user.getVerificationCode().matches("^[0-9]{6}$"), "Verification code must be 6 digits");
    }

    @Test
    void postRegister_invalidData_returnsRegisterPageWithError() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Alexander")
                        .param("username", "al") // too short (< 3)
                        .param("password", "short")) // too short (< 8)
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void getVerificationPage_returns200With6DigitBlocksAndOptions() throws Exception {
        mockMvc.perform(get("/verification").param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Verify Identity")))
                .andExpect(content().string(containsString("block1")))
                .andExpect(content().string(containsString("block6")))
                .andExpect(content().string(containsString("Resend Code")))
                .andExpect(content().string(containsString("Change email address")));
    }

    @Test
    void postVerification_valid6DigitBlocks_redirectsToLoginWithVerifiedTrue() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Test Tenant"));
        User user = new User(
                tenant.getId(),
                "Jane Doe",
                "janedoe",
                passwordEncoder.encode("Pass1234!"),
                "jane@example.com",
                false,
                "654321",
                Instant.now().plus(15, ChronoUnit.MINUTES)
        );
        userRepository.save(user);

        mockMvc.perform(post("/verification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "jane@example.com")
                        .param("digit1", "6")
                        .param("digit2", "5")
                        .param("digit3", "4")
                        .param("digit4", "3")
                        .param("digit5", "2")
                        .param("digit6", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?verified=true"));

        User updatedUser = userRepository.findByUsername("janedoe").get();
        assertTrue(updatedUser.isVerified(), "User must be verified after valid 6-digit code submission");
    }

    @Test
    void postVerification_invalidCode_returnsVerificationPageWithError() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Test Tenant"));
        User user = new User(
                tenant.getId(),
                "Jane Doe",
                "janedoe",
                passwordEncoder.encode("Pass1234!"),
                "jane@example.com",
                false,
                "654321",
                Instant.now().plus(15, ChronoUnit.MINUTES)
        );
        userRepository.save(user);

        mockMvc.perform(post("/verification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "jane@example.com")
                        .param("code", "000000"))
                .andExpect(status().isOk())
                .andExpect(view().name("verification"))
                .andExpect(model().attributeExists("error"));

        User refreshed = userRepository.findByUsername("janedoe").get();
        assertFalse(refreshed.isVerified(), "User should remain unverified on bad code");
    }

    @Test
    void postLogin_unverifiedUser_redirectsToVerification() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Preons Corp"));
        User user = new User(
                tenant.getId(),
                "Unverified User",
                "unverified",
                passwordEncoder.encode("SecretPass123!"),
                "unverified@example.com",
                false,
                "123456",
                Instant.now().plus(15, ChronoUnit.MINUTES)
        );
        userRepository.save(user);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "unverified")
                        .param("password", "SecretPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verification?email=unverified%40example.com"));
    }

    @Test
    void postLogin_validCredentialsAndVerified_setsJwtCookieAndSucceeds() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Preons Corp"));
        userRepository.save(new User(tenant.getId(), "John Doe", "johndoe", passwordEncoder.encode("SecretPass123!")));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "johndoe")
                        .param("password", "SecretPass123!"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(cookie().exists("preons_jwt"))
                .andExpect(model().attribute("loginSuccess", true));
    }

    @Test
    void postLogin_invalidCredentials_returnsLoginPageWithError() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant("Preons Corp"));
        userRepository.save(new User(tenant.getId(), "John Doe", "johndoe", passwordEncoder.encode("SecretPass123!")));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "johndoe")
                        .param("password", "WrongPassword!"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("error"));
    }
}
