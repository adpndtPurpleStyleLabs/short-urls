package com.preonsurl.apis.link.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "link_recipients", indexes = {
        @Index(name = "idx_link_recipients_short_url_id", columnList = "short_url_id"),
        @Index(name = "idx_link_recipients_tracking_token", columnList = "tracking_token", unique = true),
        @Index(name = "idx_link_recipients_short_url_email", columnList = "short_url_id, email")
})
public class LinkRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url_id", nullable = false)
    private Long shortUrlId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "tracking_token", nullable = false, unique = true, length = 64)
    private String trackingToken;

    @Column(name = "email_sent", nullable = false)
    private boolean emailSent = false;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @Column(name = "email_opened", nullable = false)
    private boolean emailOpened = false;

    @Column(name = "email_opened_at")
    private Instant emailOpenedAt;

    @Column(name = "opened_ip", length = 64)
    private String openedIp;

    @Column(name = "opened_country", length = 128)
    private String openedCountry;

    @Column(name = "opened_city", length = 128)
    private String openedCity;

    @Column(name = "opened_user_agent", length = 512)
    private String openedUserAgent;

    @Column(name = "otp_requested", nullable = false)
    private boolean otpRequested = false;

    @Column(name = "otp_requested_at")
    private Instant otpRequestedAt;

    @Column(name = "otp_code_hash", length = 255)
    private String otpCodeHash;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Column(name = "page_opened", nullable = false)
    private boolean pageOpened = false;

    @Column(name = "page_opened_at")
    private Instant pageOpenedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LinkRecipient(Long shortUrlId, String email, String trackingToken) {
        this.shortUrlId = shortUrlId;
        this.email = email != null ? email.trim().toLowerCase() : "";
        this.trackingToken = trackingToken;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
