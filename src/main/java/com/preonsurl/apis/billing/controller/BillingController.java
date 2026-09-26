package com.preonsurl.apis.billing.controller;

import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.billing.dto.*;
import com.preonsurl.apis.billing.service.BillingService;
import com.preonsurl.apis.link.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Billing and Plans", description = "Endpoints for managing user plans, Razorpay payments, billing address, and invoices")
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BillingController.class);
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @Operation(summary = "Get user billing profile and current plan", description = "Fetches current plan, price info, saved billing address, and user metadata")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<BillingProfileResponse>> getProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }
        BillingProfileResponse profile = billingService.getBillingProfile(currentUser.userId());
        return ResponseEntity.ok(ApiResponse.success(profile, "Billing profile retrieved successfully"));
    }

    @Operation(summary = "Save or update billing address", description = "Stores user billing address details for invoice generation")
    @PostMapping("/address")
    public ResponseEntity<ApiResponse<BillingAddressDto>> saveAddress(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SaveBillingAddressRequest request
    ) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }
        BillingAddressDto saved = billingService.saveBillingAddress(currentUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(saved, "Billing address saved successfully"));
    }

    @Operation(summary = "Get payment invoices", description = "Retrieves all invoice transactions for the user")
    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<List<BillingInvoiceResponse>>> getInvoices(@AuthenticationPrincipal AuthenticatedUser user) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }
        List<BillingInvoiceResponse> invoices = billingService.getInvoices(currentUser.userId());
        return ResponseEntity.ok(ApiResponse.success(invoices, "Invoices retrieved successfully"));
    }

    @Operation(summary = "View invoice HTML", description = "Renders printable luxury HTML invoice")
    @GetMapping(value = "/invoices/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getInvoiceHtml(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("id") Long id
    ) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("<h3>Unauthorized: Please login</h3>");
        }
        try {
            String html = billingService.generateInvoiceHtml(currentUser.userId(), id);
            return ResponseEntity.ok(html);
        } catch (IllegalArgumentException e) {
            log.warn("getInvoiceHtml error for userId={} id={}: {}", currentUser.userId(), id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("<h3>Invoice not found: " + e.getMessage() + "</h3>");
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("<h3>Access denied</h3>");
        }
    }

    @Operation(summary = "Create Razorpay Order for PRO plan upgrade", description = "Initializes an order with Razorpay for $50 USD payment")
    @PostMapping("/razorpay/create-order")
    public ResponseEntity<ApiResponse<RazorpayOrderResponse>> createOrder(@AuthenticationPrincipal AuthenticatedUser user) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }
        try {
            RazorpayOrderResponse order = billingService.createRazorpayOrder(currentUser.userId());
            return ResponseEntity.ok(ApiResponse.success(order, "Order created successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to initialize payment: " + e.getMessage()));
        }
    }

    @Operation(summary = "Verify Razorpay payment", description = "Validates signature and moves user to PRO plan")
    @PostMapping("/razorpay/verify-payment")
    public ResponseEntity<ApiResponse<BillingService.BillingVerificationResult>> verifyPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RazorpayVerifyRequest request
    ) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }
        try {
            BillingService.BillingVerificationResult result = billingService.verifyRazorpayPayment(currentUser.userId(), request);
            if (result.success()) {
                return ResponseEntity.ok(ApiResponse.success(result, result.message()));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(result.message(), result));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Payment verification encountered an error: " + e.getMessage()));
        }
    }

    @Operation(summary = "Record payment failure", description = "Logs failed payment attempts and dispatches notification")
    @PostMapping("/razorpay/payment-failed")
    public ResponseEntity<ApiResponse<Void>> recordPaymentFailure(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody PaymentFailureRequest request
    ) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }
        billingService.recordPaymentFailure(currentUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Failure recorded"));
    }

    private AuthenticatedUser resolveUser(AuthenticatedUser user) {
        if (user != null) {
            return user;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser authUser) {
            return authUser;
        }
        return null;
    }
}
