package com.preonsurl.apis.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.auth.JwtService;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.domain.dto.AddDomainRequest;
import com.preonsurl.apis.domain.entity.CustomDomain;
import com.preonsurl.apis.domain.entity.DomainStatus;
import com.preonsurl.apis.domain.repository.CustomDomainRepository;
import com.preonsurl.apis.domain.service.DnsVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CustomDomainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomDomainRepository customDomainRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private DnsVerificationService dnsVerificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        customDomainRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        when(dnsVerificationService.resolveCname(anyString())).thenReturn(null);

        Tenant tenant = new Tenant();
        tenant.setName("Domain Test Tenant");
        tenant = tenantRepository.save(tenant);

        testUser = new User();
        testUser.setTenantId(tenant.getId());
        testUser.setUsername("domainuser");
        testUser.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        testUser.setEmail("domainuser@example.com");
        testUser.setFullName("Domain Tester");
        testUser.setVerified(true);
        testUser = userRepository.save(testUser);

        jwtToken = jwtService.generateToken(testUser);
    }

    @Test
    void addDomain_validDomain_returnsCreated() throws Exception {
        AddDomainRequest request = new AddDomainRequest("links.mybrand.com");

        mockMvc.perform(post("/api/domains")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.domain").value("links.mybrand.com"))
                .andExpect(jsonPath("$.data.status").value("VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.data.cnameTarget").value("go.domain.com"));

        assertTrue(customDomainRepository.existsByDomain("links.mybrand.com"));
    }

    @Test
    void addDomain_normalizesProtocolAndPath() throws Exception {
        AddDomainRequest request = new AddDomainRequest("https://short.company.org/test/path");

        mockMvc.perform(post("/api/domains")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.domain").value("short.company.org"));
    }

    @Test
    void addDomain_duplicateDomain_returnsConflict() throws Exception {
        CustomDomain existing = new CustomDomain(testUser.getId(), "links.duplicate.com", "go.domain.com");
        customDomainRepository.save(existing);

        AddDomainRequest request = new AddDomainRequest("links.duplicate.com");

        mockMvc.perform(post("/api/domains")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("already registered")));
    }

    @Test
    void addDomain_invalidDomain_returnsBadRequest() throws Exception {
        AddDomainRequest request = new AddDomainRequest("invalid_domain_name");

        mockMvc.perform(post("/api/domains")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void addDomain_defaultDomain_returnsBadRequest() throws Exception {
        AddDomainRequest request = new AddDomainRequest("go.domain.com");

        mockMvc.perform(post("/api/domains")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Cannot register the system's default domain")));
    }

    @Test
    void listDomains_returnsPaginatedList() throws Exception {
        CustomDomain d1 = new CustomDomain(testUser.getId(), "links1.example.com", "go.domain.com");
        CustomDomain d2 = new CustomDomain(testUser.getId(), "links2.example.com", "go.domain.com");
        customDomainRepository.save(d1);
        customDomainRepository.save(d2);

        mockMvc.perform(get("/api/domains?page=0&size=10")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getDefaultDomain_returnsSystemDefault() throws Exception {
        mockMvc.perform(get("/api/domains/default")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.domain").value("go.domain.com"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void verifyDomain_whenCnameMatches_becomesActive() throws Exception {
        CustomDomain domain = new CustomDomain(testUser.getId(), "brand.verified.com", "go.domain.com");
        domain = customDomainRepository.save(domain);

        when(dnsVerificationService.resolveCname("brand.verified.com")).thenReturn("go.domain.com");

        mockMvc.perform(post("/api/domains/" + domain.getId() + "/verify")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        CustomDomain updated = customDomainRepository.findById(domain.getId()).orElseThrow();
        assertEquals(DomainStatus.ACTIVE, updated.getStatus());
        assertNotNull(updated.getVerifiedAt());
    }

    @Test
    void verifyDomain_whenCnameMismatches_remainsVerificationRequired() throws Exception {
        CustomDomain domain = new CustomDomain(testUser.getId(), "brand.unverified.com", "go.domain.com");
        domain = customDomainRepository.save(domain);

        when(dnsVerificationService.resolveCname("brand.unverified.com")).thenReturn("other.target.com");

        mockMvc.perform(post("/api/domains/" + domain.getId() + "/verify")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.status").value("VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.data.message").value(containsString("other.target.com")));

        CustomDomain updated = customDomainRepository.findById(domain.getId()).orElseThrow();
        assertEquals(DomainStatus.VERIFICATION_REQUIRED, updated.getStatus());
    }

    @Test
    void deleteDomain_owner_succeeds() throws Exception {
        CustomDomain domain = new CustomDomain(testUser.getId(), "todelete.com", "go.domain.com");
        domain = customDomainRepository.save(domain);

        mockMvc.perform(delete("/api/domains/" + domain.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(customDomainRepository.existsById(domain.getId()));
    }

    @Test
    void deleteDomain_nonOwner_returnsNotFound() throws Exception {
        CustomDomain domain = new CustomDomain(99999L, "otheruserdomain.com", "go.domain.com");
        domain = customDomainRepository.save(domain);

        mockMvc.perform(delete("/api/domains/" + domain.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());

        assertTrue(customDomainRepository.existsById(domain.getId()));
    }

    @Test
    void getDnsInstructions_returnsCloudflareAndGodaddy() throws Exception {
        mockMvc.perform(get("/api/domains/instructions")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[0].provider").value("Cloudflare"))
                .andExpect(jsonPath("$.data[1].provider").value("GoDaddy"));
    }

    @Test
    void checkDomainAvailability_whenFree_returnsAvailableTrue() throws Exception {
        mockMvc.perform(get("/api/domains/check?domain=available.brand.com")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.domain").value("available.brand.com"));
    }

    @Test
    void checkDomainAvailability_whenAlreadyRegistered_returnsAvailableFalse() throws Exception {
        CustomDomain existing = new CustomDomain(999L, "taken.brand.com", "go.domain.com");
        customDomainRepository.save(existing);

        mockMvc.perform(get("/api/domains/check?domain=taken.brand.com")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.message").value(containsString("already in use")));
    }

    @Test
    void checkDomainAvailability_whenDefaultDomain_returnsAvailableFalse() throws Exception {
        mockMvc.perform(get("/api/domains/check?domain=go.domain.com")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.message").value(containsString("default domain")));
    }

    @Test
    void checkDomainAvailability_whenInvalid_returnsAvailableFalse() throws Exception {
        mockMvc.perform(get("/api/domains/check?domain=invalid_domain")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.message").value(containsString("Invalid domain format")));
    }

    @Test
    void listVerifiedDomains_returnsOnlyActiveDomainsForCurrentUser() throws Exception {
        CustomDomain activeOwn = new CustomDomain(testUser.getId(), "verified.brand.com", "go.domain.com");
        activeOwn.setStatus(DomainStatus.ACTIVE);
        customDomainRepository.save(activeOwn);

        CustomDomain pendingOwn = new CustomDomain(testUser.getId(), "pending.brand.com", "go.domain.com");
        pendingOwn.setStatus(DomainStatus.VERIFICATION_REQUIRED);
        customDomainRepository.save(pendingOwn);

        CustomDomain activeOther = new CustomDomain(9999L, "other.brand.com", "go.domain.com");
        activeOther.setStatus(DomainStatus.ACTIVE);
        customDomainRepository.save(activeOther);

        mockMvc.perform(get("/api/domains/verified")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].domain").value("go.domain.com"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].domain").value("verified.brand.com"))
                .andExpect(jsonPath("$.data[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[1].isDefault").value(false));
    }
}

