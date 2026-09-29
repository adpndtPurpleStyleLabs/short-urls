package com.preonsurl.apis.link.policy.evaluator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.GeoFencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Evaluates whether visitor coordinates fall inside/outside a circular or polygonal GeoFence perimeter.
 */
@Component
public class GeoFencePolicyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GeoFencePolicyEvaluator.class);
    private static final double EARTH_RADIUS_METERS = 6371000.0; // Mean Earth radius in meters

    private final ObjectMapper objectMapper;
    private final CidrMatcher cidrMatcher;

    public GeoFencePolicyEvaluator(
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper,
            CidrMatcher cidrMatcher
    ) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.cidrMatcher = cidrMatcher;
    }

    /**
     * Evaluates access according to the configured geofence rule.
     *
     * @param clientLat Detected client latitude (from headers or GPS)
     * @param clientLng Detected client longitude (from headers or GPS)
     * @param geofenceJson JSON string of GeoFencePolicy stored in DB
     * @param clientIp Client IP address for loopback dev fallback
     * @return true if access is permitted; false if blocked by geofence rule
     */
    public boolean isAllowed(Double clientLat, Double clientLng, String geofenceJson, String clientIp) {
        if (geofenceJson == null || geofenceJson.isBlank()) {
            return true; // No geofence configured
        }

        // Loopback / local development fallback
        if ((clientLat == null || clientLng == null) && clientIp != null) {
            if (isLocalOrPrivateNetwork(clientIp)) {
                log.debug("GeoFence bypassed for local/private network IP: {}", clientIp);
                return true;
            }
        }

        GeoFencePolicy policy = parsePolicy(geofenceJson);
        if (policy == null || !policy.isValid()) {
            log.warn("Invalid geofence policy JSON encountered: {}", geofenceJson);
            return true; // Malformed policy fails open rather than blocking everything
        }

        if (clientLat == null || clientLng == null) {
            // Cannot resolve coordinates for remote client: enforce default restriction
            log.debug("Visitor coordinates could not be resolved for geofence evaluation");
            return "BLOCK".equalsIgnoreCase(policy.action()); // If policy is BLOCK inside, unknown outside is allowed
        }

        boolean inside = isInsidePerimeter(clientLat, clientLng, policy);
        boolean isBlockRule = "BLOCK".equalsIgnoreCase(policy.action());

        if (isBlockRule) {
            // Visitors INSIDE are blocked
            return !inside;
        } else {
            // Visitors INSIDE are allowed (ALLOW rule)
            return inside;
        }
    }

    /**
     * Determines whether (lat, lng) is physically inside the circle or polygon boundary.
     */
    public boolean isInsidePerimeter(double lat, double lng, GeoFencePolicy policy) {
        if ("CIRCLE".equalsIgnoreCase(policy.type())) {
            GeoFencePolicy.CircleParams circle = policy.circle();
            if (circle == null || circle.latitude() == null || circle.longitude() == null || circle.radiusMeters() == null) {
                return false;
            }
            double dist = haversineDistanceMeters(lat, lng, circle.latitude(), circle.longitude());
            return dist <= circle.radiusMeters();
        } else if ("POLYGON".equalsIgnoreCase(policy.type())) {
            GeoFencePolicy.PolygonParams poly = policy.polygon();
            if (poly == null || poly.coordinates() == null || poly.coordinates().size() < 3) {
                return false;
            }
            return isPointInPolygon(lat, lng, poly.coordinates());
        }
        return false;
    }

    /**
     * Great-circle distance using Haversine formula.
     */
    public double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Standard Ray Casting algorithm for Point-in-Polygon (Jordan curve theorem).
     */
    public boolean isPointInPolygon(double testLat, double testLng, List<GeoFencePolicy.CoordinatePoint> vertices) {
        int n = vertices.size();
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            GeoFencePolicy.CoordinatePoint vi = vertices.get(i);
            GeoFencePolicy.CoordinatePoint vj = vertices.get(j);

            if (vi.latitude() == null || vi.longitude() == null || vj.latitude() == null || vj.longitude() == null) {
                continue;
            }

            double xi = vi.latitude(), yi = vi.longitude();
            double xj = vj.latitude(), yj = vj.longitude();

            // Check if horizontal ray from test point crosses edge between vi and vj
            boolean intersect = ((yi > testLng) != (yj > testLng))
                    && (testLat < (xj - xi) * (testLng - yi) / (yj - yi) + xi);

            if (intersect) {
                inside = !inside;
            }
        }

        return inside;
    }

    public GeoFencePolicy parsePolicy(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, GeoFencePolicy.class);
        } catch (Exception e) {
            log.error("Failed to parse GeoFencePolicy JSON: {}", e.getMessage());
            return null;
        }
    }

    private boolean isLocalOrPrivateNetwork(String ip) {
        String clean = cidrMatcher.cleanIp(ip);
        if (cidrMatcher.isLoopback(clean)) {
            return true;
        }
        return clean.startsWith("10.") || clean.startsWith("192.168.") || clean.startsWith("172.16.")
                || clean.startsWith("172.17.") || clean.startsWith("172.18.") || clean.startsWith("172.19.")
                || clean.startsWith("172.2") || clean.startsWith("172.30.") || clean.startsWith("172.31.");
    }
}
