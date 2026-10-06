package com.preonsurl.apis.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.audit.dto.AuditLogResponse;
import com.preonsurl.apis.audit.entity.AuditLog;
import com.preonsurl.apis.audit.enums.AuditAction;
import com.preonsurl.apis.audit.enums.AuditResourceType;
import com.preonsurl.apis.audit.event.AuditPublisher;
import com.preonsurl.apis.audit.repository.AuditLogRepository;
import com.preonsurl.apis.auth.JwtService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditPublisher auditPublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = tenantRepository.save(new Tenant("Audit Tenant " + uniqueSuffix));
        testUser = userRepository.save(new User(tenant.getId(), "Audit User", "audit_" + uniqueSuffix, passwordEncoder.encode("Pass123!")));
        jwtToken = jwtService.generateToken(testUser);
    }

    @Test
    void testGetAuditLogsRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/audit/logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetAuditLogsEmpty() throws Exception {
        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void testPublishAndRetrieveAuditLogs() throws Exception {
        // Direct event publish
        auditPublisher.publish(
                testUser.getId(),
                testUser.getUsername(),
                testUser.getTenantId(),
                AuditAction.LINK_CREATE,
                AuditResourceType.LINK,
                "xyz123",
                "Created short link pointing to https://example.com"
        );

        // Allow async listener to persist
        Thread.sleep(300);

        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("LINK_CREATE"))
                .andExpect(jsonPath("$.data.content[0].resourceType").value("LINK"))
                .andExpect(jsonPath("$.data.content[0].resourceId").value("xyz123"))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"));
    }

    @Test
    void testFilterAuditLogsByActionAndResource() throws Exception {
        auditPublisher.publish(testUser.getId(), testUser.getUsername(), testUser.getTenantId(),
                AuditAction.USER_LOGIN, AuditResourceType.AUTH, null, "User logged in successfully");
        auditPublisher.publish(testUser.getId(), testUser.getUsername(), testUser.getTenantId(),
                AuditAction.API_KEY_ROTATE, AuditResourceType.API_KEY, "key1", "Rotated API key");
        auditPublisher.publish(testUser.getId(), testUser.getUsername(), testUser.getTenantId(),
                AuditAction.DOMAIN_CREATE, AuditResourceType.DOMAIN, "dom1", "Registered custom domain");

        Thread.sleep(300);

        // Filter by action
        mockMvc.perform(get("/api/audit/logs")
                        .param("action", "USER_LOGIN")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("USER_LOGIN"));

        // Filter by resource
        mockMvc.perform(get("/api/audit/logs")
                        .param("resourceType", "API_KEY")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("API_KEY_ROTATE"));
    }

    @Test
    void testExportAuditLogsCsv() throws Exception {
        auditPublisher.publish(testUser.getId(), testUser.getUsername(), testUser.getTenantId(),
                AuditAction.DATA_EXTRACTION_CREATE, AuditResourceType.DATA_EXTRACTION, "job_abc", "Requested CSV extraction");

        Thread.sleep(300);

        MvcResult result = mockMvc.perform(get("/api/audit/export")
                        .param("format", "csv")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"audit_logs_")))
                .andReturn();

        String csvContent = result.getResponse().getContentAsString();
        assertTrue(csvContent.contains("timestamp,action,resource_type,resource_id,status,details,ip_address,user_agent"));
        assertTrue(csvContent.contains("DATA_EXTRACTION_CREATE"));
        assertTrue(csvContent.contains("job_abc"));
        // Confirm internal ID is not present
        assertFalse(csvContent.startsWith("id,"));
    }

    @Test
    void testExportAuditLogsJson() throws Exception {
        auditPublisher.publish(testUser.getId(), testUser.getUsername(), testUser.getTenantId(),
                AuditAction.USER_LOGOUT, AuditResourceType.AUTH, null, "User logged out");

        Thread.sleep(300);

        MvcResult result = mockMvc.perform(get("/api/audit/export")
                        .param("format", "json")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"audit_logs_")))
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        assertTrue(jsonContent.contains("USER_LOGOUT"));
        assertTrue(jsonContent.contains("AUTH"));
    }
}
