package com.preonsurl.apis.link.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReverseGeocodingServiceTest {

    private ReverseGeocodingService service;

    @BeforeEach
    void setUp() {
        service = new ReverseGeocodingService(null);
    }

    @Test
    @DisplayName("formatRegion combines city, state, and country cleanly")
    void formatRegion_allComponentsPresent() {
        String region = service.formatRegion("Austin", "Texas", "United States");
        assertThat(region).isEqualTo("Austin, Texas, United States");
    }

    @Test
    @DisplayName("formatRegion handles duplicates gracefully when city matches state")
    void formatRegion_cityMatchesState() {
        String region = service.formatRegion("New York", "New York", "United States");
        assertThat(region).isEqualTo("New York, United States");
    }

    @Test
    @DisplayName("formatRegion works with only city and country")
    void formatRegion_cityAndCountryOnly() {
        String region = service.formatRegion("London", null, "United Kingdom");
        assertThat(region).isEqualTo("London, United Kingdom");
    }

    @Test
    @DisplayName("formatRegion works with only country")
    void formatRegion_countryOnly() {
        String region = service.formatRegion(null, "", "India");
        assertThat(region).isEqualTo("India");
    }

    @Test
    @DisplayName("reverseGeocode returns null for invalid coordinates")
    void reverseGeocode_invalidCoordinates() {
        assertThat(service.reverseGeocode(null, null)).isNull();
        assertThat(service.reverseGeocode(999.0, 999.0)).isNull();
    }
}
