package com.preonsurl.apis.link.entity;

import com.preonsurl.apis.link.enums.UsagePolicyType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "usage_policies", indexes = {
        @Index(name = "idx_usage_policies_short_url_id", columnList = "short_url_id"),
        @Index(name = "idx_usage_policies_expire_at", columnList = "expire_at")
})
public class UsagePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url_id", nullable = false, unique = true)
    private Long shortUrlId;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 32)
    private UsagePolicyType policyType = UsagePolicyType.UNLIMITED;

    @Column(name = "usage_limit")
    private Long usageLimit;

    @Column(name = "current_usage", nullable = false)
    private long currentUsage = 0;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UsagePolicy(Long shortUrlId, UsagePolicyType policyType, Long usageLimit, Instant expireAt, Instant startAt, Instant endAt) {
        this.shortUrlId = shortUrlId;
        this.policyType = policyType != null ? policyType : UsagePolicyType.UNLIMITED;
        this.usageLimit = usageLimit;
        this.expireAt = expireAt;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public boolean isExpired() {
        return expireAt != null && Instant.now().isAfter(expireAt);
    }

    public boolean isUsageLimitReached() {
        return usageLimit != null && currentUsage >= usageLimit;
    }

    public boolean isOutsideSchedule() {
        Instant now = Instant.now();
        if (startAt != null && now.isBefore(startAt)) {
            return true;
        }
        if (endAt != null && now.isAfter(endAt)) {
            return true;
        }
        return false;
    }

    public boolean isExhausted() {
        return isExpired() || isUsageLimitReached() || isOutsideSchedule();
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
