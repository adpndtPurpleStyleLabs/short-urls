package com.preonsurl.apis.link.policy.resolver;

import com.preonsurl.apis.link.policy.model.GeoCoordinates;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves visitor geographic coordinates from standard edge CDN / reverse proxy headers
 * or explicit query/header parameters.
 *
 * Supported edge headers:
 * - Cloudflare Enterprise / Worker: CF-IPLatitude, CF-IPLongitude
 * - AWS CloudFront / Lambda@Edge: CloudFront-Viewer-Latitude, CloudFront-Viewer-Longitude
 * - Fastly: Fastly-Client-Latitude, Fastly-Client-Longitude
 * - Akamai: X-Akamai-Edgescape (lat=...,long=...)
 * - Generic Proxies: X-Geo-Latitude, X-Geo-Longitude
 */
@Component
public class HeaderGeoCoordinatesResolver implements GeoCoordinatesResolver {

    private static final Logger log = LoggerFactory.getLogger(HeaderGeoCoordinatesResolver.class);

    private static final String[][] LAT_LNG_HEADER_PAIRS = {
            {"CF-IPLatitude", "CF-IPLongitude"},
            {"CloudFront-Viewer-Latitude", "CloudFront-Viewer-Longitude"},
            {"Fastly-Client-Latitude", "Fastly-Client-Longitude"},
            {"X-Geo-Latitude", "X-Geo-Longitude"},
            {"X-Latitude", "X-Longitude"}
    };

    @Override
    public GeoCoordinates resolveCoordinates(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // 1. Check standard header pairs
        for (String[] pair : LAT_LNG_HEADER_PAIRS) {
            String latStr = request.getHeader(pair[0]);
            String lngStr = request.getHeader(pair[1]);
            GeoCoordinates coords = parseCoords(latStr, lngStr);
            if (coords != null) {
                return coords;
            }
        }

        // 2. Check Akamai Edgescape header format: "lat=37.751,long=-122.42,..."
        String edgeScape = request.getHeader("X-Akamai-Edgescape");
        if (edgeScape != null && !edgeScape.isBlank()) {
            GeoCoordinates coords = parseEdgescape(edgeScape);
            if (coords != null) {
                return coords;
            }
        }

        // 3. Fallback: check query parameters if passed from frontend geolocation challenge
        String paramLat = request.getParameter("geo_lat");
        String paramLng = request.getParameter("geo_lng");
        return parseCoords(paramLat, paramLng);
    }

    private GeoCoordinates parseCoords(String latStr, String lngStr) {
        if (latStr == null || lngStr == null || latStr.isBlank() || lngStr.isBlank()) {
            return null;
        }
        try {
            double lat = Double.parseDouble(latStr.trim());
            double lng = Double.parseDouble(lngStr.trim());
            GeoCoordinates coords = new GeoCoordinates(lat, lng);
            return coords.isValid() ? coords : null;
        } catch (NumberFormatException e) {
            log.debug("Unparseable coordinates: lat='{}', lng='{}'", latStr, lngStr);
            return null;
        }
    }

    private GeoCoordinates parseEdgescape(String edgescape) {
        try {
            String[] parts = edgescape.split(",");
            Double lat = null;
            Double lng = null;
            for (String part : parts) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    String k = kv[0].trim().toLowerCase();
                    String v = kv[1].trim();
                    if ("lat".equals(k)) lat = Double.parseDouble(v);
                    if ("long".equals(k)) lng = Double.parseDouble(v);
                }
            }
            if (lat != null && lng != null) {
                GeoCoordinates coords = new GeoCoordinates(lat, lng);
                return coords.isValid() ? coords : null;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
