package com.preonsurl.apis.link.entity;

import com.preonsurl.apis.link.enums.LinkMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "short_urls",
        indexes = {
                @Index(name = "idx_short_urls_user_id", columnList = "user_id"),
                @Index(name = "idx_short_urls_original_url", columnList = "original_url"),
                @Index(name = "idx_short_urls_custom_path", columnList = "custom_path"),
                @Index(name = "idx_short_urls_domain", columnList = "domain"),
                @Index(name = "idx_short_urls_new_url", columnList = "new_url"),
                @Index(name = "idx_short_urls_link_mode", columnList = "link_mode"),
                @Index(name = "idx_short_urls_is_active", columnList = "is_active"),
                @Index(name = "idx_short_urls_expire_at", columnList = "expire_at"),
                @Index(name = "idx_short_urls_public_id", columnList = "public_id"),
                @Index(name = "idx_short_urls_created_by", columnList = "created_by")
        }
)
public class NewUrl {

    private static final String PUBLIC_ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int PUBLIC_ID_LENGTH = 22;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /**
     * Public identifier.
     *
     * This must be used in public APIs instead of the
     * auto-increment database ID.
     *
     * Example:
     * f8K2mP9xQ7La3VnR6Tc1Zw
     */
    @Column(
            name = "public_id",
            nullable = false,
            unique = true,
            updatable = false,
            length = 32
    )
    private String publicId;


    @Column(name = "user_id")
    private Long userId;


    @Column(
            name = "short_code",
            nullable = false,
            unique = true,
            length = 64
    )
    private String shortCode;


    @Column(
            name = "original_url",
            nullable = false,
            length = 2048
    )
    private String originalUrl;


    @Column(
            name = "custom_path",
            length = 256
    )
    private String customPath;


    @Column(
            name = "domain",
            length = 255
    )
    private String domain;


    @Column(
            name = "new_url",
            nullable = false,
            length = 512
    )
    private String newUrl;


    @Column(
            name = "click_count",
            nullable = false
    )
    private long clickCount = 0;


    @Column(
            name = "expire_at",
            nullable = false
    )
    private Instant expireAt;


    @Column(name = "usage_limit")
    private Long usageLimit;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "link_mode",
            nullable = false,
            length = 20
    )
    private LinkMode linkMode = LinkMode.REDIRECT;


    @Column(
            name = "is_active",
            nullable = false
    )
    private boolean isActive = true;


    @Column(
            name = "note",
            length = 1024
    )
    private String note;


    @Column(
            name = "created_by",
            length = 20
    )
    private String createdBy = "UI";


    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;


    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;


    // =========================================================
    // CONSTRUCTORS
    // =========================================================

    public NewUrl(
            String shortCode,
            String originalUrl,
            String customPath,
            String newUrl,
            Instant expireAt,
            Long usageLimit,
            LinkMode linkMode
    ) {
        this.publicId = generatePublicId();
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.customPath = customPath;
        this.newUrl = newUrl;
        this.expireAt = expireAt;
        this.usageLimit = usageLimit;
        this.linkMode =
                linkMode != null
                        ? linkMode
                        : LinkMode.REDIRECT;
    }


    public NewUrl(
            String shortCode,
            String originalUrl,
            String customPath,
            String domain,
            String newUrl,
            Instant expireAt,
            Long usageLimit,
            LinkMode linkMode
    ) {
        this.publicId = generatePublicId();
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.customPath = customPath;
        this.domain = domain;
        this.newUrl = newUrl;
        this.expireAt = expireAt;
        this.usageLimit = usageLimit;
        this.linkMode =
                linkMode != null
                        ? linkMode
                        : LinkMode.REDIRECT;
    }


    public NewUrl(
            String shortCode,
            String originalUrl,
            String newUrl
    ) {
        this(
                shortCode,
                originalUrl,
                null,
                newUrl,
                Instant.now().plus(
                        3650,
                        ChronoUnit.DAYS
                ),
                null,
                LinkMode.REDIRECT
        );
    }


    public NewUrl(
            String shortCode,
            String originalUrl,
            String newUrl,
            Instant expireAt
    ) {
        this(
                shortCode,
                originalUrl,
                null,
                newUrl,
                expireAt,
                null,
                LinkMode.REDIRECT
        );
    }


    public NewUrl(
            String shortCode,
            String originalUrl,
            String newUrl,
            Instant expireAt,
            Long usageLimit
    ) {
        this(
                shortCode,
                originalUrl,
                null,
                newUrl,
                expireAt,
                usageLimit,
                LinkMode.REDIRECT
        );
    }


    public NewUrl(
            String shortCode,
            String originalUrl,
            String newUrl,
            Instant expireAt,
            Long usageLimit,
            LinkMode linkMode
    ) {
        this(
                shortCode,
                originalUrl,
                null,
                newUrl,
                expireAt,
                usageLimit,
                linkMode
        );
    }


    // =========================================================
    // ACTIVE
    // =========================================================

    public boolean isActive() {
        return isActive;
    }


    public void setActive(boolean active) {
        this.isActive = active;
    }


    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }


    // =========================================================
    // PUBLIC ID GENERATION
    // =========================================================

    public static String generatePublicId() {
        StringBuilder value = new StringBuilder(PUBLIC_ID_LENGTH);
        for (int i = 0; i < PUBLIC_ID_LENGTH; i++) {
            int index = SECURE_RANDOM.nextInt(PUBLIC_ID_ALPHABET.length());
            value.append(PUBLIC_ID_ALPHABET.charAt(index));
        }

        return value.toString();
    }

    public String getPublicId() {
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = generatePublicId();
        }
        return this.publicId;
    }


    public String getCreationSource() {
        return this.createdBy != null ? this.createdBy : "UI";
    }

    public void setCreationSource(String creationSource) {
        this.createdBy = creationSource != null ? creationSource : "UI";
    }

    public String getCreatedVia() {
        return this.createdBy != null ? this.createdBy : "UI";
    }

    public void setCreatedVia(String createdVia) {
        this.createdBy = createdVia != null ? createdVia : "UI";
    }


    // =========================================================
    // JPA LIFECYCLE
    // =========================================================

    @PrePersist
    protected void onCreate() {

        Instant now = Instant.now();

        /*
         * Generate the public ID only when creating
         * a new database record.
         */
        if (this.publicId == null ||
                this.publicId.isBlank()) {

            this.publicId = generatePublicId();
        }

        if (this.createdBy == null || this.createdBy.isBlank()) {
            this.createdBy = "UI";
        }

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}