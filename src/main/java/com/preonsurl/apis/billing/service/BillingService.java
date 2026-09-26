package com.preonsurl.apis.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.auth.cache.UserCache;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.apis.billing.dto.*;
import com.preonsurl.apis.billing.entity.BillingInvoice;
import com.preonsurl.apis.billing.entity.UserBillingAddress;
import com.preonsurl.apis.billing.repository.BillingAddressRepository;
import com.preonsurl.apis.billing.repository.BillingInvoiceRepository;
import com.preonsurl.emailer.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.of("UTC"));

    private final UserRepository userRepository;
    private final BillingAddressRepository addressRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final UserCache userCache;
    private final EmailService emailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${razorpay.key.id:rzp_test_placeholder}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:secret_placeholder}")
    private String razorpayKeySecret;

    @Value("${razorpay.currency:USD}")
    private String razorpayCurrency;

    @Value("${razorpay.pro-plan-amount:5000}")
    private Long proPlanAmount; // 5000 cents = $50.00 USD

    public BillingService(
            UserRepository userRepository,
            BillingAddressRepository addressRepository,
            BillingInvoiceRepository invoiceRepository,
            UserCache userCache,
            EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.invoiceRepository = invoiceRepository;
        this.userCache = userCache;
        this.emailService = emailService;
    }

    public BillingProfileResponse getBillingProfile(Long userId) {
        User user = findUser(userId);
        UserBillingAddress address = addressRepository.findByUserId(userId).orElse(null);

        BillingAddressDto addressDto = (address != null) ? new BillingAddressDto(
                address.getFullName(),
                address.getCompany(),
                address.getEmail() != null ? address.getEmail() : user.getEmail(),
                address.getPhone(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getTaxId()
        ) : new BillingAddressDto(
                user.getFullName(),
                null,
                user.getEmail(),
                null,
                null,
                null,
                null,
                null,
                null,
                "US",
                null
        );

        String priceFormatted = formatAmount(new BigDecimal(proPlanAmount).divide(new BigDecimal(100)), razorpayCurrency);

        return new BillingProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPlan(),
                user.isPro(),
                user.getPlanUpdatedAt(),
                addressDto,
                priceFormatted,
                razorpayCurrency
        );
    }

    @Transactional
    public BillingAddressDto saveBillingAddress(Long userId, SaveBillingAddressRequest request) {
        User user = findUser(userId);
        UserBillingAddress address = addressRepository.findByUserId(userId).orElseGet(() -> {
            UserBillingAddress newAddr = new UserBillingAddress();
            newAddr.setUserId(userId);
            return newAddr;
        });

        address.setFullName(request.fullName() != null && !request.fullName().isBlank() ? request.fullName().trim() : user.getFullName());
        address.setCompany(request.company() != null ? request.company().trim() : null);
        address.setEmail(request.email() != null && !request.email().isBlank() ? request.email().trim() : user.getEmail());
        address.setPhone(request.phone() != null ? request.phone().trim() : null);
        address.setAddressLine1(request.addressLine1() != null ? request.addressLine1().trim() : null);
        address.setAddressLine2(request.addressLine2() != null ? request.addressLine2().trim() : null);
        address.setCity(request.city() != null ? request.city().trim() : null);
        address.setState(request.state() != null ? request.state().trim() : null);
        address.setPostalCode(request.postalCode() != null ? request.postalCode().trim() : null);
        address.setCountry(request.country() != null && !request.country().isBlank() ? request.country().trim() : "US");
        address.setTaxId(request.taxId() != null ? request.taxId().trim() : null);

        UserBillingAddress saved = addressRepository.save(address);

        return new BillingAddressDto(
                saved.getFullName(),
                saved.getCompany(),
                saved.getEmail(),
                saved.getPhone(),
                saved.getAddressLine1(),
                saved.getAddressLine2(),
                saved.getCity(),
                saved.getState(),
                saved.getPostalCode(),
                saved.getCountry(),
                saved.getTaxId()
        );
    }

    public List<BillingInvoiceResponse> getInvoices(Long userId) {
        List<BillingInvoice> invoices = invoiceRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return invoices.stream().map(inv -> new BillingInvoiceResponse(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getAmount(),
                inv.getCurrency(),
                formatAmount(inv.getAmount(), inv.getCurrency()),
                inv.getPlan(),
                inv.getStatus(),
                inv.getRazorpayPaymentId(),
                inv.getPaidAt(),
                inv.getCreatedAt(),
                inv.getCustomerName(),
                inv.getCustomerCompany(),
                "/api/billing/invoices/" + inv.getId() + "/html"
        )).toList();
    }

    @Transactional
    public RazorpayOrderResponse createRazorpayOrder(Long userId) {
        User user = findUser(userId);

        BigDecimal dollarAmount = new BigDecimal(proPlanAmount).divide(new BigDecimal(100));
        String invoiceNumber = generateInvoiceNumber();

        String orderId = null;
        boolean liveRazorpayConfigured = razorpayKeyId != null
                && !razorpayKeyId.isBlank()
                && !razorpayKeyId.startsWith("rzp_test_placeholder")
                && razorpayKeySecret != null
                && !"secret_placeholder".equals(razorpayKeySecret);

        if (liveRazorpayConfigured) {
            try {
                orderId = callRazorpayCreateOrderApi(proPlanAmount, razorpayCurrency, invoiceNumber);
            } catch (Exception e) {
                log.warn("Failed to create order via live Razorpay API: {}, creating fallback order", e.getMessage());
            }
        }

        if (orderId == null || orderId.isBlank()) {
            orderId = "order_sim_" + Long.toHexString(System.currentTimeMillis()) + Integer.toHexString(RANDOM.nextInt(10000, 99999));
        }

        // Snapshot existing billing address if any
        UserBillingAddress address = addressRepository.findByUserId(userId).orElse(null);

        BillingInvoice invoice = new BillingInvoice();
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setUserId(user.getId());
        invoice.setTenantId(user.getTenantId());
        invoice.setAmount(dollarAmount);
        invoice.setCurrency(razorpayCurrency);
        invoice.setPlan("PRO");
        invoice.setBillingPeriod("LIFETIME");
        invoice.setStatus("PENDING");
        invoice.setRazorpayOrderId(orderId);
        invoice.setCustomerName(address != null && address.getFullName() != null ? address.getFullName() : user.getFullName());
        invoice.setCustomerEmail(address != null && address.getEmail() != null ? address.getEmail() : user.getEmail());
        invoice.setCustomerCompany(address != null ? address.getCompany() : null);
        invoice.setCustomerAddress(address != null ? address.getAddressLine1() : null);
        invoice.setCustomerCity(address != null ? address.getCity() : null);
        invoice.setCustomerState(address != null ? address.getState() : null);
        invoice.setCustomerPostalCode(address != null ? address.getPostalCode() : null);
        invoice.setCustomerCountry(address != null ? address.getCountry() : "US");
        invoice.setCustomerTaxId(address != null ? address.getTaxId() : null);

        invoiceRepository.save(invoice);

        return new RazorpayOrderResponse(
                orderId,
                proPlanAmount,
                razorpayCurrency,
                razorpayKeyId,
                user.getEmail(),
                user.getFullName() != null ? user.getFullName() : user.getUsername(),
                "PRO",
                formatAmount(dollarAmount, razorpayCurrency)
        );
    }

    @Transactional
    public BillingVerificationResult verifyRazorpayPayment(Long userId, RazorpayVerifyRequest request) {
        User user = findUser(userId);

        String orderId = request.orderId();
        String paymentId = request.paymentId();
        String signature = request.signature();

        boolean isValid = verifySignature(orderId, paymentId, signature);

        BillingInvoice invoice = invoiceRepository.findByRazorpayOrderId(orderId)
                .orElseGet(() -> {
                    BillingInvoice fallbackInv = new BillingInvoice();
                    fallbackInv.setInvoiceNumber(generateInvoiceNumber());
                    fallbackInv.setUserId(userId);
                    fallbackInv.setTenantId(user.getTenantId());
                    fallbackInv.setAmount(new BigDecimal(proPlanAmount).divide(new BigDecimal(100)));
                    fallbackInv.setCurrency(razorpayCurrency);
                    fallbackInv.setPlan("PRO");
                    fallbackInv.setBillingPeriod("LIFETIME");
                    fallbackInv.setRazorpayOrderId(orderId);
                    fallbackInv.setCustomerName(user.getFullName());
                    fallbackInv.setCustomerEmail(user.getEmail());
                    return fallbackInv;
                });

        if (!isValid) {
            invoice.setStatus("FAILED");
            invoice.setFailureReason("Signature verification mismatch");
            invoice.setRazorpayPaymentId(paymentId);
            invoiceRepository.save(invoice);

            String formattedAmount = formatAmount(invoice.getAmount(), invoice.getCurrency());
            String dateStr = DATE_FORMATTER.format(Instant.now());
            emailService.sendPaymentFailedEmail(user.getEmail(), user.getFullName(), formattedAmount, "SecureURL PRO Plan", dateStr, "Payment signature could not be verified.");

            return new BillingVerificationResult(false, user.getPlan(), invoice.getInvoiceNumber(), "Payment verification failed. Invalid signature.");
        }

        // Mark invoice as PAID
        Instant now = Instant.now();
        invoice.setStatus("PAID");
        invoice.setPaidAt(now);
        invoice.setRazorpayPaymentId(paymentId);
        invoice.setRazorpaySignature(signature);

        // Update address snapshot if saved address exists
        addressRepository.findByUserId(userId).ifPresent(addr -> {
            if (addr.getFullName() != null) invoice.setCustomerName(addr.getFullName());
            if (addr.getCompany() != null) invoice.setCustomerCompany(addr.getCompany());
            if (addr.getEmail() != null) invoice.setCustomerEmail(addr.getEmail());
            if (addr.getAddressLine1() != null) invoice.setCustomerAddress(addr.getAddressLine1());
            if (addr.getCity() != null) invoice.setCustomerCity(addr.getCity());
            if (addr.getState() != null) invoice.setCustomerState(addr.getState());
            if (addr.getPostalCode() != null) invoice.setCustomerPostalCode(addr.getPostalCode());
            if (addr.getCountry() != null) invoice.setCustomerCountry(addr.getCountry());
            if (addr.getTaxId() != null) invoice.setCustomerTaxId(addr.getTaxId());
        });

        invoiceRepository.save(invoice);

        // Upgrade user to PRO
        user.setPlan("PRO");
        user.setPlanUpdatedAt(now);
        userRepository.save(user);
        userCache.put(user);

        log.info("User '{}' (ID: {}) successfully upgraded to PRO plan via Razorpay payment '{}'", user.getUsername(), user.getId(), paymentId);

        // Send confirmation email with receipt
        String formattedAmount = formatAmount(invoice.getAmount(), invoice.getCurrency());
        String dateStr = DATE_FORMATTER.format(now);
        emailService.sendPaymentSuccessEmail(
                user.getEmail(),
                user.getFullName() != null ? user.getFullName() : user.getUsername(),
                invoice.getInvoiceNumber(),
                formattedAmount,
                "PRO",
                dateStr,
                paymentId
        );

        return new BillingVerificationResult(true, "PRO", invoice.getInvoiceNumber(), "Payment verified successfully. Welcome to PRO!");
    }

    @Transactional
    public void recordPaymentFailure(Long userId, PaymentFailureRequest request) {
        User user = findUser(userId);

        Optional<BillingInvoice> optInvoice = (request.orderId() != null)
                ? invoiceRepository.findByRazorpayOrderId(request.orderId())
                : invoiceRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");

        String reason = (request.errorDescription() != null && !request.errorDescription().isBlank())
                ? request.errorDescription()
                : (request.errorReason() != null ? request.errorReason() : "Payment was declined or cancelled");

        BillingInvoice inv = optInvoice.orElseGet(() -> {
            BillingInvoice fallback = new BillingInvoice();
            fallback.setInvoiceNumber(generateInvoiceNumber());
            fallback.setUserId(userId);
            fallback.setTenantId(user.getTenantId());
            fallback.setAmount(new BigDecimal(proPlanAmount).divide(new BigDecimal(100)));
            fallback.setCurrency(razorpayCurrency);
            fallback.setPlan("PRO");
            fallback.setBillingPeriod("LIFETIME");
            fallback.setRazorpayOrderId(request.orderId());
            fallback.setCustomerName(user.getFullName());
            fallback.setCustomerEmail(user.getEmail());
            return fallback;
        });

        inv.setStatus("FAILED");
        inv.setFailureReason(reason);
        if (request.paymentId() != null) {
            inv.setRazorpayPaymentId(request.paymentId());
        }
        invoiceRepository.save(inv);

        String formattedAmount = formatAmount(new BigDecimal(proPlanAmount).divide(new BigDecimal(100)), razorpayCurrency);
        String dateStr = DATE_FORMATTER.format(Instant.now());
        emailService.sendPaymentFailedEmail(user.getEmail(), user.getFullName(), formattedAmount, "SecureURL PRO Plan", dateStr, reason);
    }

    public String generateInvoiceHtml(Long userId, Long invoiceId) {
        BillingInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found with ID: " + invoiceId));

        if (!invoice.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized access to invoice");
        }

        User user = findUser(userId);
        String customerName = invoice.getCustomerName() != null ? invoice.getCustomerName() : user.getFullName();
        String customerEmail = invoice.getCustomerEmail() != null ? invoice.getCustomerEmail() : user.getEmail();
        String company = invoice.getCustomerCompany() != null ? invoice.getCustomerCompany() : "";
        String address = invoice.getCustomerAddress() != null ? invoice.getCustomerAddress() : "N/A";
        String cityState = (invoice.getCustomerCity() != null ? invoice.getCustomerCity() : "") +
                (invoice.getCustomerState() != null ? ", " + invoice.getCustomerState() : "") +
                (invoice.getCustomerPostalCode() != null ? " " + invoice.getCustomerPostalCode() : "");
        String country = invoice.getCustomerCountry() != null ? invoice.getCustomerCountry() : "US";
        String taxId = invoice.getCustomerTaxId() != null ? invoice.getCustomerTaxId() : "";

        String invoiceDate = invoice.getCreatedAt() != null ? DATE_FORMATTER.format(invoice.getCreatedAt()) : "N/A";
        String paymentId = invoice.getRazorpayPaymentId() != null ? invoice.getRazorpayPaymentId() : "N/A";
        String amountFormatted = formatAmount(invoice.getAmount(), invoice.getCurrency());
        boolean isPaid = "PAID".equalsIgnoreCase(invoice.getStatus());

        return buildLuxuryInvoiceHtml(
                invoice.getInvoiceNumber(),
                invoiceDate,
                invoice.getStatus(),
                isPaid,
                customerName,
                customerEmail,
                company,
                address,
                cityState,
                country,
                taxId,
                paymentId,
                amountFormatted,
                invoice.getPlan()
        );
    }

    private boolean verifySignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null) {
            return false;
        }

        // Test / sandbox fallback
        if (orderId.startsWith("order_sim_") || "secret_placeholder".equals(razorpayKeySecret) || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            return true;
        }

        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            String payload = orderId + "|" + paymentId;
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);
            byte[] hash = sha256HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(hash);
            return expected.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Error computing HMAC-SHA256 signature for Razorpay verification: {}", e.getMessage());
            return false;
        }
    }

    private String callRazorpayCreateOrderApi(Long amountInCents, String currency, String receipt) throws Exception {
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (razorpayKeyId + ":" + razorpayKeySecret).getBytes(StandardCharsets.UTF_8)
        );

        String jsonPayload = String.format("""
            {
                "amount": %d,
                "currency": "%s",
                "receipt": "%s",
                "payment_capture": 1
            }
        """, amountInCents, currency, receipt);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/orders"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(resp.body());
            if (root.has("id")) {
                return root.get("id").asText();
            }
        }
        log.warn("Razorpay API returned non-2xx status code {}: {}", resp.statusCode(), resp.body());
        return null;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
    }

    private String generateInvoiceNumber() {
        return "INV-2026-" + Integer.toHexString(RANDOM.nextInt(0x100000, 0xFFFFFF)).toUpperCase();
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if ("USD".equalsIgnoreCase(currency)) {
            return "$" + amount.setScale(2).toPlainString();
        }
        return amount.setScale(2).toPlainString() + " " + currency;
    }

    private String buildLuxuryInvoiceHtml(
            String invoiceNumber,
            String date,
            String status,
            boolean isPaid,
            String name,
            String email,
            String company,
            String address,
            String cityState,
            String country,
            String taxId,
            String paymentId,
            String amountFormatted,
            String plan
    ) {
        String statusBadge = isPaid
                ? "<span style='display:inline-block;padding:6px 16px;border-radius:20px;font-size:12px;font-weight:700;letter-spacing:1px;background:rgba(16,185,129,0.15);color:#10b981;border:1px solid rgba(16,185,129,0.3);text-transform:uppercase;'>PAID</span>"
                : "<span style='display:inline-block;padding:6px 16px;border-radius:20px;font-size:12px;font-weight:700;letter-spacing:1px;background:rgba(239,68,68,0.15);color:#ef4444;border:1px solid rgba(239,68,68,0.3);text-transform:uppercase;'>" + status + "</span>";

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Invoice {{titleInvoiceNumber}} - SecureURL</title>
                <style>
                    body {
                        margin: 0;
                        padding: 40px 20px;
                        background: #0f111a;
                        color: #e2e8f0;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    }
                    .invoice-wrapper {
                        max-width: 800px;
                        margin: 0 auto;
                        background: #141724;
                        border: 1px solid rgba(255, 255, 255, 0.08);
                        border-radius: 16px;
                        padding: 48px;
                        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
                    }
                    .header-grid {
                        display: flex;
                        justify-content: space-between;
                        align-items: flex-start;
                        border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                        padding-bottom: 32px;
                        margin-bottom: 32px;
                    }
                    .brand-title {
                        font-size: 26px;
                        font-weight: 800;
                        color: #ffffff;
                        letter-spacing: -0.5px;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                    .brand-logo {
                        width: 36px;
                        height: 36px;
                        background: linear-gradient(135deg, #7c3aed 0%, #a855f7 100%);
                        color: white;
                        border-radius: 8px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-weight: 900;
                        font-size: 18px;
                    }
                    .company-meta {
                        color: #94a3b8;
                        font-size: 13px;
                        margin-top: 8px;
                        line-height: 1.5;
                    }
                    .invoice-meta {
                        text-align: right;
                    }
                    .invoice-num {
                        font-family: monospace;
                        font-size: 20px;
                        font-weight: 700;
                        color: #ffffff;
                        margin-bottom: 8px;
                    }
                    .invoice-date {
                        color: #94a3b8;
                        font-size: 13px;
                        margin-bottom: 12px;
                    }
                    .billing-details-grid {
                        display: flex;
                        justify-content: space-between;
                        margin-bottom: 40px;
                    }
                    .detail-col {
                        flex: 1;
                    }
                    .detail-title {
                        font-size: 11px;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                        color: #a855f7;
                        font-weight: 700;
                        margin-bottom: 10px;
                    }
                    .detail-val {
                        color: #f8fafc;
                        font-size: 14px;
                        line-height: 1.6;
                    }
                    .detail-val.muted {
                        color: #94a3b8;
                    }
                    .items-table {
                        width: 100%%;
                        border-collapse: collapse;
                        margin-bottom: 32px;
                    }
                    .items-table th {
                        text-align: left;
                        padding: 12px 16px;
                        border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                        color: #94a3b8;
                        font-size: 12px;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }
                    .items-table td {
                        padding: 18px 16px;
                        border-bottom: 1px solid rgba(255, 255, 255, 0.05);
                        font-size: 14px;
                    }
                    .item-title {
                        color: #ffffff;
                        font-weight: 600;
                        margin-bottom: 4px;
                    }
                    .item-desc {
                        color: #94a3b8;
                        font-size: 12px;
                        line-height: 1.4;
                    }
                    .summary-box {
                        width: 320px;
                        margin-left: auto;
                        background: rgba(255, 255, 255, 0.02);
                        border: 1px solid rgba(255, 255, 255, 0.06);
                        border-radius: 12px;
                        padding: 20px;
                        margin-bottom: 36px;
                    }
                    .summary-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 8px 0;
                        font-size: 14px;
                        color: #94a3b8;
                    }
                    .summary-row.total {
                        border-top: 1px solid rgba(255, 255, 255, 0.1);
                        padding-top: 14px;
                        margin-top: 6px;
                        color: #ffffff;
                        font-size: 18px;
                        font-weight: 700;
                    }
                    .actions-bar {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-top: 1px solid rgba(255, 255, 255, 0.08);
                        padding-top: 24px;
                    }
                    .print-btn {
                        background: #000000;
                        color: white;
                        border: 1px solid #000000;
                        padding: 10px 24px;
                        border-radius: 8px;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }
                    .print-btn:hover {
                        background: #262626;
                        border-color: #262626;
                    }
                    @media print {
                        body { background: #ffffff; color: #000000; padding: 0; }
                        .invoice-wrapper { border: none; box-shadow: none; padding: 0; background: #ffffff; color: #000000; }
                        .actions-bar { display: none; }
                        .summary-box { background: #f8fafc; border: 1px solid #e2e8f0; color: #000; }
                        .brand-title, .invoice-num, .summary-row.total, .item-title, .detail-val { color: #000000 !important; }
                    }
                </style>
            </head>
            <body>
                <div class="invoice-wrapper">
                    <div class="header-grid">
                        <div>
                            <div class="brand-title">
                                <div class="brand-logo">P</div>
                                <span>PreonsURL Platform</span>
                            </div>
                            <div class="company-meta">
                                Preons Digital Infrastructure Inc.<br>
                                Cloud Routing &amp; Link Security Engine<br>
                                support@indexrender.io
                            </div>
                        </div>
                        <div class="invoice-meta">
                            <div class="invoice-num">{{invoiceNumber}}</div>
                            <div class="invoice-date">{{invoiceDate}}</div>
                            <div>{{statusBadge}}</div>
                        </div>
                    </div>

                    <div class="billing-details-grid">
                        <div class="detail-col">
                            <div class="detail-title">BILLED TO</div>
                            <div class="detail-val" style="font-weight:600;">{{customerName}}</div>
                            <div class="detail-val muted">{{customerCompany}}</div>
                            <div class="detail-val muted">{{customerEmail}}</div>
                            <div class="detail-val muted">{{customerAddress}}</div>
                            <div class="detail-val muted">{{cityStateCountry}}</div>
                            {{taxIdHtml}}
                        </div>
                        <div class="detail-col" style="text-align: right;">
                            <div class="detail-title">PAYMENT DETAILS</div>
                            <div class="detail-val">Provider: <strong>Razorpay</strong></div>
                            <div class="detail-val muted">Payment ID: <code style="color:#c4b5fd;">{{paymentId}}</code></div>
                            <div class="detail-val muted">Plan License: <strong>{{plan}} (Lifetime)</strong></div>
                            <div class="detail-val muted">Status: <strong style="color:#10b981;">PAID IN FULL</strong></div>
                        </div>
                    </div>

                    <table class="items-table">
                        <thead>
                            <tr>
                                <th>Item Description</th>
                                <th style="text-align:center;">Qty</th>
                                <th style="text-align:right;">Rate</th>
                                <th style="text-align:right;">Amount</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>
                                    <div class="item-title">SecureURL PRO Plan — Lifetime License</div>
                                    <div class="item-desc">
                                        Full suite unlocked: Unlimited URL shortening, Custom Domains with automated SSL, REST API key access, Reverse Proxy and Asset Rewrite Mirroring, Custom Slugs, and priority security policies.
                                    </div>
                                </td>
                                <td style="text-align:center;">1</td>
                                <td style="text-align:right;">{{rateAmount}}</td>
                                <td style="text-align:right; font-weight:700; color:#ffffff;">{{itemAmount}}</td>
                            </tr>
                        </tbody>
                    </table>

                    <div class="summary-box">
                        <div class="summary-row">
                            <span>Subtotal</span>
                            <span>{{subtotalAmount}}</span>
                        </div>
                        <div class="summary-row">
                            <span>Tax (0%)</span>
                            <span>$0.00</span>
                        </div>
                        <div class="summary-row total">
                            <span>Total Paid</span>
                            <span style="color:#a855f7;">{{totalAmount}}</span>
                        </div>
                    </div>

                    <div class="actions-bar">
                        <span style="font-size:12px; color:#64748b;">
                            Thank you for partnering with PreonsURL. This document serves as official proof of payment.
                        </span>
                        <button class="print-btn" onclick="window.print()">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <polyline points="6 9 6 2 18 2 18 9"></polyline>
                                <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path>
                                <rect x="6" y="14" width="12" height="8"></rect>
                            </svg>
                            Print Invoice
                        </button>
                    </div>
                </div>
            </body>
            </html>
            """
            .replace("{{titleInvoiceNumber}}", invoiceNumber)
            .replace("{{invoiceNumber}}", invoiceNumber)
            .replace("{{invoiceDate}}", date)
            .replace("{{statusBadge}}", statusBadge)
            .replace("{{customerName}}", name != null ? name : "Valued Customer")
            .replace("{{customerCompany}}", company != null && !company.isBlank() ? company + "<br>" : "")
            .replace("{{customerEmail}}", email != null ? email : "")
            .replace("{{customerAddress}}", address != null && !address.isBlank() ? address + "<br>" : "")
            .replace("{{cityStateCountry}}", cityState.isBlank() ? country : cityState + ", " + country)
            .replace("{{taxIdHtml}}", taxId.isBlank() ? "" : "<div class='detail-val muted'>Tax ID: " + taxId + "</div>")
            .replace("{{paymentId}}", paymentId != null ? paymentId : "N/A")
            .replace("{{plan}}", plan != null ? plan : "PRO")
            .replace("{{rateAmount}}", amountFormatted)
            .replace("{{itemAmount}}", amountFormatted)
            .replace("{{subtotalAmount}}", amountFormatted)
            .replace("{{totalAmount}}", amountFormatted);
    }

    public record BillingVerificationResult(
            boolean success,
            String plan,
            String invoiceNumber,
            String message
    ) {
    }
}
