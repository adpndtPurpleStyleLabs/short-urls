package com.preonsurl.apis.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "billing_invoices",
        indexes = {
                @Index(name = "idx_billing_invoices_user_id", columnList = "user_id"),
                @Index(name = "idx_billing_invoices_invoice_number", columnList = "invoice_number"),
                @Index(name = "idx_billing_invoices_order_id", columnList = "razorpay_order_id")
        }
)
public class BillingInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 64)
    private String invoiceNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 10, nullable = false)
    private String currency = "USD";

    @Column(name = "plan", length = 20, nullable = false)
    private String plan = "PRO";

    @Column(name = "billing_period", length = 30)
    private String billingPeriod = "LIFETIME";

    @Column(name = "status", length = 30, nullable = false)
    private String status = "PENDING"; // PENDING, PAID, FAILED

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_company", length = 150)
    private String customerCompany;

    @Column(name = "customer_address", length = 255)
    private String customerAddress;

    @Column(name = "customer_city", length = 100)
    private String customerCity;

    @Column(name = "customer_state", length = 100)
    private String customerState;

    @Column(name = "customer_postal_code", length = 30)
    private String customerPostalCode;

    @Column(name = "customer_country", length = 100)
    private String customerCountry;

    @Column(name = "customer_tax_id", length = 50)
    private String customerTaxId;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
