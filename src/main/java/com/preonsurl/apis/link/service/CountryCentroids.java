package com.preonsurl.apis.link.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides approximate geographic center coordinates (latitude, longitude) and standard country names
 * for ISO 3166-1 alpha-2 country codes. Used as fallback when exact browser coordinates are unavailable.
 */
public final class CountryCentroids {

    public record Coordinates(double latitude, double longitude, String countryName) {}

    private static final Map<String, Coordinates> CENTROIDS;

    static {
        Map<String, Coordinates> map = new HashMap<>();
        map.put("US", new Coordinates(37.0902, -95.7129, "United States"));
        map.put("IN", new Coordinates(20.5937, 78.9629, "India"));
        map.put("GB", new Coordinates(55.3781, -3.4360, "United Kingdom"));
        map.put("CA", new Coordinates(56.1304, -106.3468, "Canada"));
        map.put("AU", new Coordinates(-25.2744, 133.7751, "Australia"));
        map.put("DE", new Coordinates(51.1657, 10.4515, "Germany"));
        map.put("FR", new Coordinates(46.2276, 2.2137, "France"));
        map.put("JP", new Coordinates(36.2048, 138.2529, "Japan"));
        map.put("CN", new Coordinates(35.8617, 104.1954, "China"));
        map.put("BR", new Coordinates(-14.2350, -51.9253, "Brazil"));
        map.put("SG", new Coordinates(1.3521, 103.8198, "Singapore"));
        map.put("AE", new Coordinates(23.4241, 53.8478, "United Arab Emirates"));
        map.put("NL", new Coordinates(52.1326, 5.2913, "Netherlands"));
        map.put("SE", new Coordinates(60.1282, 18.6435, "Sweden"));
        map.put("CH", new Coordinates(46.8182, 8.2275, "Switzerland"));
        map.put("IT", new Coordinates(41.8719, 12.5674, "Italy"));
        map.put("ES", new Coordinates(40.4637, -3.7492, "Spain"));
        map.put("RU", new Coordinates(61.5240, 105.3188, "Russia"));
        map.put("ZA", new Coordinates(-30.5595, 22.9375, "South Africa"));
        map.put("MX", new Coordinates(23.6345, -102.5528, "Mexico"));
        map.put("ID", new Coordinates(-0.7893, 113.9213, "Indonesia"));
        map.put("KR", new Coordinates(35.9078, 127.7669, "South Korea"));
        map.put("TR", new Coordinates(38.9637, 35.2433, "Turkey"));
        map.put("SA", new Coordinates(23.8859, 45.0792, "Saudi Arabia"));
        map.put("PL", new Coordinates(51.9194, 19.1451, "Poland"));
        map.put("BE", new Coordinates(50.5039, 4.4699, "Belgium"));
        map.put("AT", new Coordinates(47.5162, 14.5501, "Austria"));
        map.put("NO", new Coordinates(60.4720, 8.4689, "Norway"));
        map.put("DK", new Coordinates(56.2639, 9.5018, "Denmark"));
        map.put("FI", new Coordinates(61.9241, 25.7482, "Finland"));
        map.put("IE", new Coordinates(53.1424, -7.6921, "Ireland"));
        map.put("NZ", new Coordinates(-40.9006, 174.8860, "New Zealand"));
        map.put("PT", new Coordinates(39.3999, -8.2245, "Portugal"));
        map.put("GR", new Coordinates(39.0742, 21.8243, "Greece"));
        map.put("IL", new Coordinates(31.0461, 34.8516, "Israel"));
        map.put("MY", new Coordinates(4.2105, 101.9758, "Malaysia"));
        map.put("TH", new Coordinates(15.8700, 100.9925, "Thailand"));
        map.put("VN", new Coordinates(14.0583, 108.2772, "Vietnam"));
        map.put("PH", new Coordinates(12.8797, 121.7740, "Philippines"));
        map.put("EG", new Coordinates(26.8206, 30.8025, "Egypt"));
        map.put("NG", new Coordinates(9.0820, 8.6753, "Nigeria"));
        map.put("PK", new Coordinates(30.3753, 69.3451, "Pakistan"));
        map.put("BD", new Coordinates(23.6850, 90.3563, "Bangladesh"));
        map.put("AR", new Coordinates(-38.4161, -63.6167, "Argentina"));
        map.put("CL", new Coordinates(-35.6751, -71.5430, "Chile"));
        map.put("CO", new Coordinates(4.5709, -74.2973, "Colombia"));
        map.put("PE", new Coordinates(-9.1899, -75.0152, "Peru"));
        map.put("CZ", new Coordinates(49.8175, 15.4730, "Czech Republic"));
        map.put("RO", new Coordinates(45.9432, 24.9668, "Romania"));
        map.put("HU", new Coordinates(47.1625, 19.5033, "Hungary"));
        map.put("UA", new Coordinates(48.3794, 31.1656, "Ukraine"));
        CENTROIDS = Collections.unmodifiableMap(map);
    }

    private CountryCentroids() {}

    public static Coordinates get(String countryCode) {
        if (countryCode == null) return null;
        return CENTROIDS.get(countryCode.trim().toUpperCase());
    }
}
