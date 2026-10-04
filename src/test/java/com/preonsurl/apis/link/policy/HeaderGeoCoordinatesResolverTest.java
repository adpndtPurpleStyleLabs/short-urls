package com.preonsurl.apis.link.policy;

import com.preonsurl.apis.link.policy.model.GeoCoordinates;
import com.preonsurl.apis.link.policy.resolver.HeaderGeoCoordinatesResolver;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class HeaderGeoCoordinatesResolverTest {

    private HeaderGeoCoordinatesResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new HeaderGeoCoordinatesResolver();
    }

    @Test
    @DisplayName("Resolves from Cloudflare CF-IPLatitude and CF-IPLongitude headers")
    void testCloudflareHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-IPLatitude", "37.7749");
        request.addHeader("CF-IPLongitude", "-122.4194");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        assertEquals(37.7749, coords.latitude(), 0.0001);
        assertEquals(-122.4194, coords.longitude(), 0.0001);
        assertTrue(coords.isValid());
    }

    @Test
    @DisplayName("Resolves from CloudFront Viewer headers")
    void testCloudFrontHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CloudFront-Viewer-Latitude", "19.0760");
        request.addHeader("CloudFront-Viewer-Longitude", "72.8777");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        assertEquals(19.0760, coords.latitude(), 0.0001);
        assertEquals(72.8777, coords.longitude(), 0.0001);
        assertTrue(coords.isValid());
    }

    @Test
    @DisplayName("Resolves from Google App Engine X-AppEngine-CityLatLong header")
    void testAppEngineHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-AppEngine-CityLatLong", "28.6139,77.2090");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        assertEquals(28.6139, coords.latitude(), 0.0001);
        assertEquals(77.2090, coords.longitude(), 0.0001);
        assertTrue(coords.isValid());
    }

    @Test
    @DisplayName("Resolves from Akamai X-Akamai-Edgescape header")
    void testAkamaiEdgescapeHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Akamai-Edgescape", "country_code=US,lat=40.7128,long=-74.0060,city=NEWYORK");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        assertEquals(40.7128, coords.latitude(), 0.0001);
        assertEquals(-74.0060, coords.longitude(), 0.0001);
        assertTrue(coords.isValid());
    }

    @Test
    @DisplayName("Resolves from query/form parameters geo_lat and geo_lng")
    void testQueryParams() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("geo_lat", "51.5074");
        request.setParameter("geo_lng", "-0.1278");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        assertEquals(51.5074, coords.latitude(), 0.0001);
        assertEquals(-0.1278, coords.longitude(), 0.0001);
        assertTrue(coords.isValid());
    }

    @Test
    @DisplayName("Resolves from client session cookie PREONS_GEO_COORDS")
    void testCookieCoords() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("PREONS_GEO_COORDS", "35.6762,139.6503"));

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        assertEquals(35.6762, coords.latitude(), 0.0001);
        assertEquals(139.6503, coords.longitude(), 0.0001);
        assertTrue(coords.isValid());
    }

    @Test
    @DisplayName("Auto-corrects inverted latitude and longitude if |lat| > 90 and |lng| <= 90")
    void testAutoCorrectInvertedCoords() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("lat", "122.4194");
        request.setParameter("lng", "37.7749");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        assertEquals(37.7749, coords.latitude(), 0.0001);
        assertEquals(122.4194, coords.longitude(), 0.0001);
        assertTrue(coords.isValid());
    }

    @Test
    @DisplayName("Discards coordinates out of global range")
    void testOutOfBounds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("lat", "999.0");
        request.setParameter("lng", "999.0");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNull(coords);
    }

    @Test
    @DisplayName("Browser query parameters take priority over Cloudflare edge headers")
    void testBrowserParamsPriorityOverCloudflareHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Edge CDN header from Cloudflare
        request.addHeader("CF-IPLatitude", "28.6139");
        request.addHeader("CF-IPLongitude", "77.2090");

        // Precise browser GPS query parameters
        request.setParameter("geo_lat", "19.0760");
        request.setParameter("geo_lng", "72.8777");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        // Must match browser parameters, NOT Cloudflare edge headers
        assertEquals(19.0760, coords.latitude(), 0.0001);
        assertEquals(72.8777, coords.longitude(), 0.0001);
    }

    @Test
    @DisplayName("Browser session cookie takes priority over CloudFront edge headers")
    void testBrowserCookiePriorityOverCloudFrontHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Edge CDN header from CloudFront
        request.addHeader("CloudFront-Viewer-Latitude", "40.7128");
        request.addHeader("CloudFront-Viewer-Longitude", "-74.0060");

        // Browser session cookie set after location permission granted
        request.setCookies(new Cookie("PREONS_GEO_COORDS", "12.9716,77.5946"));

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        // Must match browser cookie, NOT CloudFront headers
        assertEquals(12.9716, coords.latitude(), 0.0001);
        assertEquals(77.5946, coords.longitude(), 0.0001);
    }

    @Test
    @DisplayName("Browser client headers take priority over Fastly and AppEngine headers")
    void testBrowserCustomHeadersPriorityOverFastlyAndAppEngine() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Fastly-Client-Latitude", "51.5074");
        request.addHeader("Fastly-Client-Longitude", "-0.1278");
        request.addHeader("X-AppEngine-CityLatLong", "51.5074,-0.1278");

        request.addHeader("X-Browser-Latitude", "34.0522");
        request.addHeader("X-Browser-Longitude", "-118.2437");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        // Must match browser header, NOT edge headers
        assertEquals(34.0522, coords.latitude(), 0.0001);
        assertEquals(-118.2437, coords.longitude(), 0.0001);
    }

    @Test
    @DisplayName("Resolves from combined browser param geo_coords")
    void testBrowserCombinedParam() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("geo_coords", "13.0827,80.2707");

        GeoCoordinates coords = resolver.resolveCoordinates(request);
        assertNotNull(coords);
        assertEquals(13.0827, coords.latitude(), 0.0001);
        assertEquals(80.2707, coords.longitude(), 0.0001);
    }
}
