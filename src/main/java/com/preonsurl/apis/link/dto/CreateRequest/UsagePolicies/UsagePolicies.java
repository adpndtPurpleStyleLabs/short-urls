package com.preonsurl.apis.link.dto.CreateRequest.UsagePolicies;

import com.preonsurl.apis.link.enums.UsagePolicyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.time.Instant;

@Schema(
        description = """
                Policies controlling how many times, when, and until when
                a link can be used.
                """
)
public record UsagePolicies(

        @Schema(
                description = """
                        Usage policy type:
                        UNLIMITED allows unlimited access,
                        ONE_TIME allows exactly one access,
                        USAGE_LIMIT allows access up to the specified usageLimit.
                        """,
                example = "USAGE_LIMIT",
                defaultValue = "UNLIMITED"
        )
        UsagePolicyType type,

        @Min(
                value = 1,
                message = "usageLimit must be greater than zero"
        )
        @Schema(
                description = "Maximum number of times the link can be accessed. Required when type is USAGE_LIMIT.",
                example = "100"
        )
        Long usageLimit,

        @Schema(
                description = "Timestamp after which the link can no longer be accessed. UTC ISO-8601.",
                example = "2026-12-31T23:59:59Z"
        )
        Instant expireAt,

        @Valid
        @Schema(
                description = """
                        Optional time window during which the link is accessible.
                        Both startAt and endAt must be provided and startAt must
                        be before endAt.
                        """
        )
        AccessSchedule schedule

) {

    public static UsagePolicies unlimited() {
        return new UsagePolicies(
                UsagePolicyType.UNLIMITED,
                null,
                null,
                null
        );
    }

    public static UsagePolicies oneTime() {
        return new UsagePolicies(
                UsagePolicyType.ONE_TIME,
                1L,
                null,
                null
        );
    }

    public static UsagePolicies limited(long usageLimit) {

        if (usageLimit <= 0) {
            throw new IllegalArgumentException(
                    "usageLimit must be greater than zero"
            );
        }

        return new UsagePolicies(
                UsagePolicyType.USAGE_LIMIT,
                usageLimit,
                null,
                null
        );
    }

    public boolean isUnlimited() {
        return type == null ||
                type == UsagePolicyType.UNLIMITED;
    }

    public boolean isOneTime() {
        return type == UsagePolicyType.ONE_TIME;
    }

    public boolean hasUsageLimit() {
        return type == UsagePolicyType.USAGE_LIMIT;
    }

    public boolean hasExpiry() {
        return expireAt != null;
    }

    public boolean hasSchedule() {
        return schedule != null;
    }

    public Long resolvedUsageLimit() {

        if (isUnlimited()) {
            return null;
        }

        if (isOneTime()) {
            return 1L;
        }

        if (hasUsageLimit()) {

            if (usageLimit == null || usageLimit <= 0) {
                throw new IllegalArgumentException(
                        "usageLimit is required and must be greater than zero " +
                                "when usage policy is USAGE_LIMIT"
                );
            }

            return usageLimit;
        }

        return null;
    }

    public Instant resolvedExpireAt() {

        if (expireAt == null) {
            return null;
        }

        if (!expireAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "expireAt must be greater than the current UTC time"
            );
        }

        return expireAt;
    }

    public AccessSchedule resolvedSchedule() {

        if (schedule == null) {
            return null;
        }

        return schedule.validate();
    }

    public UsagePolicyType resolvedType() {
        return type == null
                ? UsagePolicyType.UNLIMITED
                : type;
    }

    public UsagePolicies validate() {
        resolvedType();
        resolvedUsageLimit();
        resolvedExpireAt();
        resolvedSchedule();
        return this;
    }
}