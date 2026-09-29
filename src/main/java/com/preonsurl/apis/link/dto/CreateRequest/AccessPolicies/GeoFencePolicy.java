package com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Geographic boundary perimeter (circle or polygon)")
public record GeoFencePolicy(

        @Schema(description = "Shape type: CIRCLE or POLYGON", example = "CIRCLE")
        String type,

        @Schema(description = "Action when visitor is inside perimeter: ALLOW or BLOCK", example = "ALLOW")
        String action,

        @Schema(description = "Optional label or description for the boundary", example = "Tech Hub Perimeter")
        String name,

        @Schema(description = "Circular boundary parameters")
        CircleParams circle,

        @Schema(description = "Polygon boundary parameters")
        PolygonParams polygon

) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CircleParams(
            Double latitude,
            Double longitude,
            Integer radiusMeters
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolygonParams(
            List<CoordinatePoint> coordinates
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CoordinatePoint(
            Double latitude,
            Double longitude
    ) {}

    public boolean isValid() {
        if (type == null) return false;
        if ("CIRCLE".equalsIgnoreCase(type)) {
            return circle != null && circle.latitude() != null && circle.longitude() != null && circle.radiusMeters() != null && circle.radiusMeters() > 0;
        } else if ("POLYGON".equalsIgnoreCase(type)) {
            return polygon != null && polygon.coordinates() != null && polygon.coordinates().size() >= 3;
        }
        return false;
    }
}
