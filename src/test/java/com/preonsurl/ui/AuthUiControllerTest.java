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

import static org.hamcrest.Matchers.containsString;
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
    void postRegister_validData_delegatesToAuthControllerAndRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Alexander Hamilton")
                        .param("username", "ahamilton")
                        .param("password", "Treasury2026!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));

        // Verify user was registered in DB
        assertTrue(userRepository.findByUsername("ahamilton").isPresent(), "User should be registered in database");
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
    void postLogin_validCredentials_setsJwtCookieAndSucceeds() throws Exception {
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
