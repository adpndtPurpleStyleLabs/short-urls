package com.preonsurl.apis.link.policy.model;

/**
 * Encapsulates geographic latitude, longitude, and optional accuracy in meters.
 */
public record GeoCoordinates(
        Double latitude,
        Double longitude,
        Double accuracy
) {
    public GeoCoordinates(Double latitude, Double longitude) {
        this(latitude, longitude, null);
    }

    public boolean isValid() {
        return latitude != null && longitude != null
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }
}
