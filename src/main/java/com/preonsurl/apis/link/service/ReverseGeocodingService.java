package com.preonsurl.apis.link.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to reverse geocode geographic coordinates (latitude, longitude)
 * into city, state, country and a human-readable region string.
 * Uses BigDataCloud reverse geocoding API with in-memory caching and OSM Nominatim fallback.
 */
@Service
public class ReverseGeocodingService {

    private static final Logger log = LoggerFactory.getLogger(ReverseGeocodingService.class);
    private static final int MAX_CACHE_SIZE = 5000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, ReverseGeocodeResult> cache = new ConcurrentHashMap<>();

    public record ReverseGeocodeResult(String city, String state, String country, String region) {}

    public ReverseGeocodingService(@Autowired(required = false) ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2500))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Resolves coordinates to city, state, country, and formatted region.
     * Thread-safe, non-blocking to the caller on errors, and cached by rounded coordinates.
     */
    public ReverseGeocodeResult reverseGeocode(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }

        double lat = latitude;
        double lng = longitude;

        // Auto-fix inverted coordinates if necessary
        if (Math.abs(lat) > 90.0 && Math.abs(lng) <= 90.0) {
            double tmp = lat;
            lat = lng;
            lng = tmp;
        }

        if (Math.abs(lat) > 90.0 || Math.abs(lng) > 180.0) {
            return null;
        }

        String cacheKey = String.format(Locale.US, "%.3f,%.3f", lat, lng);
        ReverseGeocodeResult cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ReverseGeocodeResult result = fetchFromBigDataCloud(lat, lng);
        if (result == null) {
            result = fetchFromNominatim(lat, lng);
        }

        if (result != null) {
            if (cache.size() >= MAX_CACHE_SIZE) {
                cache.clear();
            }
            cache.put(cacheKey, result);
        }

        return result;
    }

    /**
     * Formats city, state, and country into a clean region string: "City, State, Country".
     */
    public String formatRegion(String city, String state, String country) {
        List<String> parts = new ArrayList<>();
        if (city != null && !city.isBlank()) {
            parts.add(city.trim());
        }
        if (state != null && !state.isBlank()) {
            String cleanState = state.trim();
            if (parts.isEmpty() || !parts.get(0).equalsIgnoreCase(cleanState)) {
                parts.add(cleanState);
            }
        }
        if (country != null && !country.isBlank()) {
            String cleanCountry = country.trim();
            boolean duplicate = parts.stream().anyMatch(p -> p.equalsIgnoreCase(cleanCountry));
            if (!duplicate) {
                parts.add(cleanCountry);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private ReverseGeocodeResult fetchFromBigDataCloud(double lat, double lng) {
        try {
            String url = String.format(Locale.US,
                    "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.6f&longitude=%.6f&localityLanguage=en",
                    lat, lng);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(3000))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                JsonNode root = objectMapper.readTree(response.body());

                String city = textOrNull(root.get("city"));
                if (city == null || city.isBlank()) {
                    city = textOrNull(root.get("locality"));
                }
                String state = textOrNull(root.get("principalSubdivision"));
                String country = textOrNull(root.get("countryName"));

                String region = formatRegion(city, state, country);
                if (region != null) {
                    return new ReverseGeocodeResult(city, state, country, region);
                }
            }
        } catch (Exception e) {
            log.debug("BigDataCloud reverse geocode call failed for ({}, {}): {}", lat, lng, e.getMessage());
        }
        return null;
    }

    private ReverseGeocodeResult fetchFromNominatim(double lat, double lng) {
        try {
            String url = String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=10",
                    lat, lng);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(3000))
                    .header("User-Agent", "PreonsUrl/1.0 (ReverseGeocodingService)")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "en")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode addr = root.path("address");

                String city = firstNonNullText(addr, "city", "town", "village", "municipality", "county");
                String state = firstNonNullText(addr, "state", "state_district", "region", "province");
                String country = textOrNull(addr.get("country"));

                String region = formatRegion(city, state, country);
                if (region != null) {
                    return new ReverseGeocodeResult(city, state, country, region);
                }
            }
        } catch (Exception e) {
            log.debug("Nominatim reverse geocode call failed for ({}, {}): {}", lat, lng, e.getMessage());
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        if (node != null && !node.isNull()) {
            String val = node.asText().trim();
            return val.isEmpty() ? null : val;
        }
        return null;
    }

    private String firstNonNullText(JsonNode parent, String... keys) {
        if (parent == null || parent.isNull()) return null;
        for (String k : keys) {
            String v = textOrNull(parent.get(k));
            if (v != null) return v;
        }
        return null;
    }
}
