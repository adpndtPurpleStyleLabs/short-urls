package com.preonsurl.apis.link.policy.resolver;

import com.preonsurl.apis.link.policy.model.GeoCoordinates;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves visitor geographic coordinates from standard edge CDN / reverse proxy headers,
 * explicit query/header parameters, or client session cookies.
 *
 * Supported sources (prioritizing browser/client direct location over CDN edge estimations):
 * 1. Browser/Client Query & Form Parameters: geo_lat/geo_lng, geo_latitude/geo_longitude, latitude/longitude, lat/lng, lat/lon, browser_lat/browser_lng, client_lat/client_lng
 * 2. Browser/Client Request Headers: X-Browser-Latitude/X-Browser-Longitude, X-Client-Latitude/X-Client-Longitude, X-Browser-LatLong, X-Browser-Geo
 * 3. Browser Client Session Cookies (set upon geolocation permission): PREONS_GEO_COORDS, PREONS_BROWSER_GEO, PREONS_GEO_LAT/PREONS_GEO_LNG, BROWSER_GEO_COORDS
 * 4. Edge CDN / Proxy IP Geolocation Headers (Fallback):
 *    - Cloudflare Enterprise / Worker: CF-IPLatitude, CF-IPLongitude
 *    - AWS CloudFront / Lambda@Edge: CloudFront-Viewer-Latitude, CloudFront-Viewer-Longitude
 *    - Fastly: Fastly-Client-Latitude, Fastly-Client-Longitude
 *    - Google Cloud App Engine: X-AppEngine-CityLatLong ("lat,long")
 *    - Akamai: X-Akamai-Edgescape (lat=...,long=...)
 *    - Generic Proxies: X-Geo-Latitude, X-Geo-Longitude, X-Latitude, X-Longitude
 */
@Component
public class HeaderGeoCoordinatesResolver implements GeoCoordinatesResolver {

    private static final Logger log = LoggerFactory.getLogger(HeaderGeoCoordinatesResolver.class);

    private static final String[][] BROWSER_PARAM_PAIRS = {
            {"geo_lat", "geo_lng"},
            {"geo_latitude", "geo_longitude"},
            {"latitude", "longitude"},
            {"lat", "lng"},
            {"lat", "lon"},
            {"browser_lat", "browser_lng"},
            {"client_lat", "client_lng"},
            {"user_lat", "user_lng"}
    };

    private static final String[] BROWSER_COMBINED_PARAMS = {
            "geo_coords",
            "coords",
            "coordinates",
            "geolocation",
            "latlng",
            "lat_lng"
    };

    private static final String[][] BROWSER_HEADER_PAIRS = {
            {"X-Browser-Latitude", "X-Browser-Longitude"},
            {"X-Client-Latitude", "X-Client-Longitude"},
            {"X-Device-Latitude", "X-Device-Longitude"}
    };

    private static final String[] BROWSER_COMBINED_HEADERS = {
            "X-Browser-LatLong",
            "X-Browser-GeoCoords",
            "X-Browser-Geo",
            "X-Client-Geo",
            "Geolocation"
    };

    private static final String[] BROWSER_COMBINED_COOKIE_NAMES = {
            "PREONS_GEO_COORDS",
            "PREONS_BROWSER_GEO",
            "BROWSER_GEO_COORDS",
            "GEO_COORDS"
    };

    private static final String[][] BROWSER_COOKIE_PAIRS = {
            {"PREONS_GEO_LAT", "PREONS_GEO_LNG"},
            {"BROWSER_GEO_LAT", "BROWSER_GEO_LNG"}
    };

    private static final String[][] CDN_EDGE_HEADER_PAIRS = {
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

        // 1. HIGHEST PRIORITY: Browser / client provided coordinates (HTML5 Geolocation / direct client input)
        GeoCoordinates browserCoords = resolveBrowserCoordinates(request);
        if (browserCoords != null) {
            log.debug("Using browser-provided coordinates: lat={}, lng={}", browserCoords.latitude(), browserCoords.longitude());
            return browserCoords;
        }

        // 2. FALLBACK PRIORITY: Edge CDN / reverse proxy headers (IP-based coarse estimation)
        GeoCoordinates edgeCoords = resolveEdgeCoordinates(request);
        if (edgeCoords != null) {
            log.debug("Using edge CDN fallback coordinates: lat={}, lng={}", edgeCoords.latitude(), edgeCoords.longitude());
            return edgeCoords;
        }

        return null;
    }

    /**
     * Resolves coordinates explicitly provided by the browser (query params, form params,
     * client headers, or session cookies persisted from HTML5 Geolocation API permission).
     */
    public GeoCoordinates resolveBrowserCoordinates(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // Extract optional accuracy from request headers
        String headerAccuracy = extractAccuracyHeader(request);

        // Extract optional accuracy from query parameters
        String paramAccuracy = extractAccuracyParam(request);

        // 1. Check browser/client query or form parameters
        for (String[] paramPair : BROWSER_PARAM_PAIRS) {
            String paramLat = request.getParameter(paramPair[0]);
            String paramLng = request.getParameter(paramPair[1]);
            GeoCoordinates coords = parseCoords(paramLat, paramLng, paramAccuracy);
            if (coords != null) {
                return coords;
            }
        }

        // Check single combined parameters (e.g. "geo_coords=19.0760,72.8777,15.5")
        for (String paramName : BROWSER_COMBINED_PARAMS) {
            String combined = request.getParameter(paramName);
            GeoCoordinates coords = parseCombinedCoords(combined);
            if (coords != null) {
                if (coords.accuracy() == null && paramAccuracy != null) {
                    try {
                        coords = new GeoCoordinates(coords.latitude(), coords.longitude(), Double.parseDouble(paramAccuracy));
                    } catch (Exception ignored) {}
                }
                return coords;
            }
        }

        // 2. Check browser/client specific request headers
        for (String[] headerPair : BROWSER_HEADER_PAIRS) {
            String headerLat = request.getHeader(headerPair[0]);
            String headerLng = request.getHeader(headerPair[1]);
            GeoCoordinates coords = parseCoords(headerLat, headerLng, headerAccuracy);
            if (coords != null) {
                return coords;
            }
        }

        for (String headerName : BROWSER_COMBINED_HEADERS) {
            String combined = request.getHeader(headerName);
            GeoCoordinates coords = parseCombinedCoords(combined);
            if (coords != null) {
                if (coords.accuracy() == null && headerAccuracy != null) {
                    try {
                        coords = new GeoCoordinates(coords.latitude(), coords.longitude(), Double.parseDouble(headerAccuracy));
                    } catch (Exception ignored) {}
                }
                return coords;
            }
        }

        // 3. Check cookies stored from client-side browser geolocation permission
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            // First check combined cookies (e.g. PREONS_GEO_COORDS=19.0760,72.8777,15.5 or 19.0760_72.8777_15.5)
            for (String cookieName : BROWSER_COMBINED_COOKIE_NAMES) {
                for (Cookie c : cookies) {
                    if (cookieName.equalsIgnoreCase(c.getName())) {
                        GeoCoordinates coords = parseCombinedCoords(c.getValue());
                        if (coords != null) {
                            return coords;
                        }
                    }
                }
            }

            // Then check split cookie pairs (e.g. PREONS_GEO_LAT and PREONS_GEO_LNG)
            for (String[] pair : BROWSER_COOKIE_PAIRS) {
                String cLat = null;
                String cLng = null;
                for (Cookie c : cookies) {
                    if (pair[0].equalsIgnoreCase(c.getName())) {
                        cLat = c.getValue();
                    } else if (pair[1].equalsIgnoreCase(c.getName())) {
                        cLng = c.getValue();
                    }
                }
                if (cLat != null && cLng != null) {
                    GeoCoordinates coords = parseCoords(cLat, cLng, headerAccuracy);
                    if (coords != null) {
                        return coords;
                    }
                }
            }
        }

        return null;
    }

    private String extractAccuracyHeader(HttpServletRequest request) {
        if (request == null) return null;
        for (String name : new String[]{"X-Browser-Accuracy", "X-Client-Accuracy", "X-Device-Accuracy", "X-Geo-Accuracy", "Accuracy"}) {
            String val = request.getHeader(name);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return null;
    }

    private String extractAccuracyParam(HttpServletRequest request) {
        if (request == null) return null;
        for (String name : new String[]{"geo_accuracy", "geo_acc", "accuracy", "acc"}) {
            String val = request.getParameter(name);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return null;
    }

    /**
     * Resolves fallback approximate coordinates from edge CDN / reverse proxy headers.
     */
    public GeoCoordinates resolveEdgeCoordinates(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String edgeAccuracy = extractAccuracyHeader(request);

        // 1. Check standard CDN / proxy header pairs
        for (String[] pair : CDN_EDGE_HEADER_PAIRS) {
            String latStr = request.getHeader(pair[0]);
            String lngStr = request.getHeader(pair[1]);
            GeoCoordinates coords = parseCoords(latStr, lngStr, edgeAccuracy);
            if (coords != null) {
                return coords;
            }
        }

        // 2. Check Google AppEngine header format: "37.386051,-122.083855"
        String appEngineLatLong = request.getHeader("X-AppEngine-CityLatLong");
        if (appEngineLatLong != null && !appEngineLatLong.isBlank()) {
            GeoCoordinates coords = parseCombinedCoords(appEngineLatLong);
            if (coords != null) {
                return coords;
            }
        }

        // 3. Check Akamai Edgescape header format: "lat=37.751,long=-122.42,..."
        String edgeScape = request.getHeader("X-Akamai-Edgescape");
        if (edgeScape != null && !edgeScape.isBlank()) {
            GeoCoordinates coords = parseEdgescape(edgeScape);
            if (coords != null) {
                return coords;
            }
        }

        return null;
    }

    public GeoCoordinates parseCombinedCoords(String combined) {
        if (combined == null || combined.isBlank()) {
            return null;
        }
        String clean = combined.trim();
        clean = clean.replace("%2C", ",").replace("%2c", ",");
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 1) {
            clean = clean.substring(1, clean.length() - 1);
        }
        String[] parts = null;
        if (clean.contains(",")) {
            parts = clean.split(",");
        } else if (clean.contains("_")) {
            parts = clean.split("_");
        } else if (clean.contains(" ") || clean.contains(";")) {
            parts = clean.split("[;\\s]+");
        }
        if (parts != null && parts.length >= 2) {
            String acc = parts.length >= 3 ? parts[2] : null;
            return parseCoords(parts[0], parts[1], acc);
        }
        return null;
    }

    public GeoCoordinates parseCoords(String latStr, String lngStr) {
        return parseCoords(latStr, lngStr, null);
    }

    public GeoCoordinates parseCoords(String latStr, String lngStr, String accuracyStr) {
        if (latStr == null || lngStr == null || latStr.isBlank() || lngStr.isBlank()) {
            return null;
        }
        try {
            double lat = Double.parseDouble(latStr.trim());
            double lng = Double.parseDouble(lngStr.trim());
            Double accuracy = null;
            if (accuracyStr != null && !accuracyStr.isBlank()) {
                try {
                    accuracy = Double.parseDouble(accuracyStr.trim());
                    if (accuracy < 0) {
                        accuracy = null;
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Auto-correct inverted latitude and longitude if necessary
            if (Math.abs(lat) > 90.0 && Math.abs(lng) <= 90.0) {
                double tmp = lat;
                lat = lng;
                lng = tmp;
            }

            GeoCoordinates coords = new GeoCoordinates(lat, lng, accuracy);
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
                if (Math.abs(lat) > 90.0 && Math.abs(lng) <= 90.0) {
                    double tmp = lat;
                    lat = lng;
                    lng = tmp;
                }
                GeoCoordinates coords = new GeoCoordinates(lat, lng);
                return coords.isValid() ? coords : null;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
