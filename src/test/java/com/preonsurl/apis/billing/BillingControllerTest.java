package com.preonsurl.apis.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.auth.JwtService;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.billing.dto.PaymentFailureRequest;
import com.preonsurl.apis.billing.dto.RazorpayVerifyRequest;
import com.preonsurl.apis.billing.dto.SaveBillingAddressRequest;
import com.preonsurl.apis.billing.entity.BillingInvoice;
import com.preonsurl.apis.billing.repository.BillingAddressRepository;
import com.preonsurl.apis.billing.repository.BillingInvoiceRepository;
import com.preonsurl.emailer.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BillingAddressRepository billingAddressRepository;

    @Autowired
    private BillingInvoiceRepository billingInvoiceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        billingInvoiceRepository.deleteAll();
        billingAddressRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        // Stub email service
        doNothing().when(emailService).sendPaymentSuccessEmail(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
        doNothing().when(emailService).sendPaymentFailedEmail(anyString(), anyString(), anyString(), anyString(), anyString(), any());

        Tenant tenant = new Tenant();
        tenant.setName("Billing Test Tenant");
        tenant = tenantRepository.save(tenant);

        testUser = new User();
        testUser.setTenantId(tenant.getId());
        testUser.setUsername("billinguser");
        testUser.setEmail("billing@example.com");
        testUser.setFullName("Billing Tester");
        testUser.setPasswordHash(passwordEncoder.encode("SecretPass123!"));
        testUser.setVerified(true);
        testUser.setPlan("FREE");
        testUser = userRepository.save(testUser);

        jwtToken = "Bearer " + jwtService.generateToken(testUser);
    }

    @Test
    @DisplayName("User defaults to FREE plan on profile retrieval")
    void testGetBillingProfileDefaultsToFree() throws Exception {
        mockMvc.perform(get("/api/billing/profile")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.plan", is("FREE")))
                .andExpect(jsonPath("$.data.email", is("billing@example.com")))
                .andExpect(jsonPath("$.data.isPro", is(false)));
    }

    @Test
    @DisplayName("Save and retrieve billing address")
    void testSaveBillingAddress() throws Exception {
        SaveBillingAddressRequest request = new SaveBillingAddressRequest(
                "John Doe",
                "Acme Inc",
                "invoices@acme.com",
                "+1234567890",
                "100 Innovation Way",
                null,
                "San Francisco",
                "CA",
                "94107",
                "United States",
                "US-TAX-9988"
        );

        mockMvc.perform(post("/api/billing/address")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.company", is("Acme Inc")))
                .andExpect(jsonPath("$.data.city", is("San Francisco")));

        // Verify profile returns the updated address
        mockMvc.perform(get("/api/billing/profile")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address.company", is("Acme Inc")))
                .andExpect(jsonPath("$.data.address.taxId", is("US-TAX-9988")));
    }

    @Test
    @DisplayName("Create Razorpay Order for PRO upgrade")
    void testCreateRazorpayOrder() throws Exception {
        mockMvc.perform(post("/api/billing/razorpay/create-order")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderId", notNullValue()))
                .andExpect(jsonPath("$.data.amount", is(5000)))
                .andExpect(jsonPath("$.data.currency", is("USD")));
    }

    @Test
    @DisplayName("Verify Razorpay Payment upgrades user to PRO and creates invoice")
    void testVerifyRazorpayPayment() throws Exception {
        // Step 1: Initialize order
        String orderResponse = mockMvc.perform(post("/api/billing/razorpay/create-order")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(orderResponse).path("data").path("orderId").asText();

        // Step 2: Verify payment with simulated signature
        RazorpayVerifyRequest verifyRequest = new RazorpayVerifyRequest(orderId, "pay_test_998877", "simulated_valid_signature");

        mockMvc.perform(post("/api/billing/razorpay/verify-payment")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.plan", is("PRO")))
                .andExpect(jsonPath("$.data.invoiceNumber", notNullValue()));

        // Step 3: Verify User is upgraded in Database
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals("PRO", updatedUser.getPlan());
        assertTrue(updatedUser.isPro());

        // Step 4: Verify Profile endpoint reports PRO
        mockMvc.perform(get("/api/billing/profile")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan", is("PRO")))
                .andExpect(jsonPath("$.data.isPro", is(true)));

        // Step 5: Verify Invoices list includes the paid invoice
        mockMvc.perform(get("/api/billing/invoices")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status", is("PAID")))
                .andExpect(jsonPath("$.data[0].plan", is("PRO")));

        // Step 6: Verify HTML Invoice rendering
        BillingInvoice inv = billingInvoiceRepository.findAll().get(0);
        mockMvc.perform(get("/api/billing/invoices/" + inv.getId() + "/html")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PreonsURL Platform")))
                .andExpect(content().string(containsString(inv.getInvoiceNumber())))
                .andExpect(content().string(containsString("PAID")));
    }

    @Test
    @DisplayName("Record Razorpay payment failure")
    void testRecordPaymentFailure() throws Exception {
        PaymentFailureRequest failureRequest = new PaymentFailureRequest(
                "order_failed_test_123",
                "pay_failed_456",
                "BAD_REQUEST_ERROR",
                "Card was declined by bank",
                "payment_failed"
        );

        mockMvc.perform(post("/api/billing/razorpay/payment-failed")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failureRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify a FAILED invoice entry is registered in database
        assertTrue(billingInvoiceRepository.findAll().stream()
                .anyMatch(i -> "FAILED".equals(i.getStatus()) && "Card was declined by bank".equals(i.getFailureReason())));
    }

    @Test
    @DisplayName("Verify Razorpay Payment succeeds with camelCase frontend payload (razorpayOrderId / razorpayPaymentId)")
    void testVerifyRazorpayPaymentWithCamelCaseAliases() throws Exception {
        // Step 1: Initialize order
        String orderResponse = mockMvc.perform(post("/api/billing/razorpay/create-order")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(orderResponse).path("data").path("orderId").asText();

        // Step 2: Send camelCase payload matching Razorpay JS response keys
        String jsonPayload = """
                {
                    "razorpayOrderId": "%s",
                    "razorpayPaymentId": "pay_test_camelcase_123",
                    "razorpaySignature": "simulated_valid_signature"
                }
                """.formatted(orderId);

        mockMvc.perform(post("/api/billing/razorpay/verify-payment")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.plan", is("PRO")));
    }

    @Test
    @DisplayName("Verify Razorpay Payment returns 400 Bad Request with field errors when required fields are missing")
    void testVerifyRazorpayPaymentValidationFailure() throws Exception {
        String invalidPayload = """
                {
                    "signature": "simulated_valid_signature"
                }
                """;

        mockMvc.perform(post("/api/billing/razorpay/verify-payment")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("orderId")))
                .andExpect(jsonPath("$.status", is(400)));
    }
}

