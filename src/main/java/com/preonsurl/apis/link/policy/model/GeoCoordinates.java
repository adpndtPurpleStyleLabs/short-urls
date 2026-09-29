package com.preonsurl.apis.link.policy.model;

/**
 * Encapsulates geographic latitude and longitude coordinates.
 */
public record GeoCoordinates(
        Double latitude,
        Double longitude
) {
    public boolean isValid() {
        return latitude != null && longitude != null
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }
}
