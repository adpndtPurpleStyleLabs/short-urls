package com.preonsurl.apis.link.dto.CreateRequest.UsagePolicies;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(
        description = "Defines the time window during which a link is accessible"
)
public record AccessSchedule(
        @Schema(
                description = "Start of the access window in UTC ISO-8601 format",
                example = "2026-09-21T09:00:00Z"
        )
        Instant startAt,

        @Schema(
                description = "End of the access window in UTC ISO-8601 format",
                example = "2026-09-21T18:00:00Z"
        )
        Instant endAt

) {

    public AccessSchedule validate() {

        if (startAt == null) {
            throw new IllegalArgumentException(
                    "schedule.startAt is required"
            );
        }

        if (endAt == null) {
            throw new IllegalArgumentException(
                    "schedule.endAt is required"
            );
        }

        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException(
                    "schedule.endAt must be greater than schedule.startAt"
            );
        }

        return this;
    }

    public boolean hasStarted() {
        return Instant.now().isAfter(startAt);
    }

    public boolean hasEnded() {
        return Instant.now().isAfter(endAt);
    }

    public boolean isCurrentlyActive() {

        Instant now = Instant.now();

        return !now.isBefore(startAt)
                && !now.isAfter(endAt);
    }
}