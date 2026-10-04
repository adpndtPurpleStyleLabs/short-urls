package com.preonsurl.apis.link.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Analytics and telemetry collection preferences for a link")
public record AnalyticsPreferences(
        @Schema(description = "Whether to capture and ensure visitor location/coordinates", example = "true", defaultValue = "true")
        Boolean trackLocation,

        @Schema(description = "Whether to capture visitor user-agent/browser/OS details", example = "true", defaultValue = "true")
        Boolean trackUserAgent,

        @Schema(description = "Whether to capture visitor IP address", example = "true", defaultValue = "true")
        Boolean trackIp,

        @Schema(description = "Whether to capture HTTP referrer origin", example = "true", defaultValue = "true")
        Boolean trackReferrer
) {
    public boolean resolvedTrackLocation() { return trackLocation == null || trackLocation; }
    public boolean resolvedTrackUserAgent() { return trackUserAgent == null || trackUserAgent; }
    public boolean resolvedTrackIp() { return trackIp == null || trackIp; }
    public boolean resolvedTrackReferrer() { return trackReferrer == null || trackReferrer; }

    public static AnalyticsPreferences allEnabled() {
        return new AnalyticsPreferences(true, true, true, true);
    }
}
