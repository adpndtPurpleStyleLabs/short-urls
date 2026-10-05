package com.preonsurl.apis.dataExtraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.auth.JwtService;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.dataExtraction.dto.CreateExtractionRequest;
import com.preonsurl.apis.dataExtraction.enums.ExtractionDataType;
import com.preonsurl.apis.dataExtraction.enums.ExtractionReportType;
import com.preonsurl.apis.dataExtraction.repository.DataExtractionJobRepository;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DataExtractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataExtractionJobRepository jobRepository;

    @Autowired
    private NewUrlRepository newUrlRepository;

    @Autowired
    private NewUrlAccessLogRepository accessLogRepository;

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
        jobRepository.deleteAll();
        accessLogRepository.deleteAll();
        newUrlRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = new Tenant();
        tenant.setName("Extract Test Tenant");
        tenant = tenantRepository.save(tenant);

        testUser = new User();
        testUser.setTenantId(tenant.getId());
        testUser.setUsername("extractuser");
        testUser.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        testUser.setEmail("extractuser@example.com");
        testUser.setFullName("Extract Tester");
        testUser.setVerified(true);
        testUser = userRepository.save(testUser);

        jwtToken = jwtService.generateToken(testUser);
    }

    @Test
    void testExtractLinksCsvGeneratesAndDownloadsFile() throws Exception {
        NewUrl link1 = new NewUrl("code1", "https://example.com/one", "http://localhost:8081/code1");
        link1.setUserId(testUser.getId());
        newUrlRepository.save(link1);

        NewUrl link2 = new NewUrl("code2", "https://example.com/two", "http://localhost:8081/code2");
        link2.setUserId(testUser.getId());
        newUrlRepository.save(link2);

        CreateExtractionRequest request = new CreateExtractionRequest(
                ExtractionDataType.LINK,
                ExtractionReportType.CSV,
                null,
                null,
                null,
                null
        );

        String responseJson = mockMvc.perform(post("/api/extract")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicId").exists())
                .andExpect(jsonPath("$.data.dataType").value("LINK"))
                .andExpect(jsonPath("$.data.reportType").value("CSV"))
                .andReturn().getResponse().getContentAsString();

        String publicId = objectMapper.readTree(responseJson).path("data").path("publicId").asText();
        assertNotNull(publicId);

        // Wait for background extraction to complete
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            var job = jobRepository.findByPublicId(publicId);
            return job.isPresent() && "COMPLETED".equals(job.get().getStatus().name());
        });

        // Test listing jobs
        mockMvc.perform(get("/api/extract/jobs")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.content[0].publicId").value(publicId))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].recordCount").value(2));

        // Test download
        mockMvc.perform(get("/api/extract/download/" + publicId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment; filename=\"links_export_")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("public_id,short_url,short_code")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id,public_id"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://example.com/one")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://example.com/two")));
    }

    @Test
    void testExtractAnalyticsJsonGeneratesAndDownloadsFile() throws Exception {
        NewUrl link = new NewUrl("analyticsCode", "https://example.com/target", "http://localhost:8081/analyticsCode");
        link.setUserId(testUser.getId());
        NewUrl savedLink = newUrlRepository.save(link);

        NewUrlAccessLog logEntry = new NewUrlAccessLog(
                savedLink.getId(),
                "analyticsCode",
                "1.2.3.4",
                "Mozilla/5.0",
                "https://google.com",
                "India",
                "Mumbai",
                19.076,
                72.877,
                "Mobile",
                "Chrome",
                "Android"
        );
        accessLogRepository.save(logEntry);

        CreateExtractionRequest request = new CreateExtractionRequest(
                ExtractionDataType.LINK_ANALYTICS,
                ExtractionReportType.JSON,
                null,
                null,
                savedLink.getId(),
                null
        );

        String responseJson = mockMvc.perform(post("/api/extract")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicId").exists())
                .andReturn().getResponse().getContentAsString();

        String publicId = objectMapper.readTree(responseJson).path("data").path("publicId").asText();

        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            var job = jobRepository.findByPublicId(publicId);
            return job.isPresent() && "COMPLETED".equals(job.get().getStatus().name());
        });

        // Test download JSON
        mockMvc.perform(get("/api/extract/download/" + publicId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".json")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].shortCode").value("analyticsCode"))
                .andExpect(jsonPath("$[0].country").value("India"))
                .andExpect(jsonPath("$[0].city").value("Mumbai"));
    }

    @Test
    void testExtractAnalyticsMultipleLinksJson() throws Exception {
        NewUrl link1 = new NewUrl("codeAlpha", "https://example.com/alpha", "http://localhost:8081/codeAlpha");
        link1.setUserId(testUser.getId());
        link1 = newUrlRepository.save(link1);

        NewUrl link2 = new NewUrl("codeBeta", "https://example.com/beta", "http://localhost:8081/codeBeta");
        link2.setUserId(testUser.getId());
        link2 = newUrlRepository.save(link2);

        NewUrl link3Unselected = new NewUrl("codeGamma", "https://example.com/gamma", "http://localhost:8081/codeGamma");
        link3Unselected.setUserId(testUser.getId());
        link3Unselected = newUrlRepository.save(link3Unselected);

        NewUrlAccessLog log1 = new NewUrlAccessLog(
                link1.getId(), "codeAlpha", "1.1.1.1", "Mozilla/5.0", "https://google.com",
                "India", "Delhi", 28.61, 77.20, "Desktop", "Chrome", "Linux"
        );
        NewUrlAccessLog log2 = new NewUrlAccessLog(
                link2.getId(), "codeBeta", "2.2.2.2", "Mozilla/5.0", "https://bing.com",
                "United States", "New York", 40.71, -74.00, "Mobile", "Safari", "iOS"
        );
        NewUrlAccessLog log3 = new NewUrlAccessLog(
                link3Unselected.getId(), "codeGamma", "3.3.3.3", "Mozilla/5.0", "https://yahoo.com",
                "United Kingdom", "London", 51.50, -0.12, "Desktop", "Firefox", "macOS"
        );
        accessLogRepository.save(log1);
        accessLogRepository.save(log2);
        accessLogRepository.save(log3);

        CreateExtractionRequest request = new CreateExtractionRequest(
                ExtractionDataType.LINK_ANALYTICS,
                ExtractionReportType.JSON,
                null,
                null,
                null,
                java.util.List.of(link1.getId(), link2.getId()),
                null
        );

        String responseJson = mockMvc.perform(post("/api/extract")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicId").exists())
                .andReturn().getResponse().getContentAsString();

        String publicId = objectMapper.readTree(responseJson).path("data").path("publicId").asText();

        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            var job = jobRepository.findByPublicId(publicId);
            return job.isPresent() && "COMPLETED".equals(job.get().getStatus().name());
        });

        // Test download JSON and verify only link1 and link2 records are present (2 records, not link3)
        mockMvc.perform(get("/api/extract/download/" + publicId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].shortCode", org.hamcrest.Matchers.containsInAnyOrder("codeAlpha", "codeBeta")));
    }
}
