package com.preonsurl.apis.link.event;

import com.preonsurl.apis.link.cache.NewUrlLruCache;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.repository.UsagePolicyRepository;
import com.preonsurl.apis.link.service.ReverseGeocodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ShortUrlEventListenerTest {

    private NewUrlRepository repository;
    private NewUrlAccessLogRepository accessLogRepository;
    private NewUrlLruCache lruCache;
    private UsagePolicyRepository usagePolicyRepository;
    private ReverseGeocodingService reverseGeocodingService;
    private ShortUrlEventListener listener;

    @BeforeEach
    void setUp() {
        repository = mock(NewUrlRepository.class);
        accessLogRepository = mock(NewUrlAccessLogRepository.class);
        lruCache = mock(NewUrlLruCache.class);
        usagePolicyRepository = mock(UsagePolicyRepository.class);
        reverseGeocodingService = mock(ReverseGeocodingService.class);

        listener = new ShortUrlEventListener(
                repository,
                accessLogRepository,
                lruCache,
                usagePolicyRepository,
                null,
                reverseGeocodingService
        );
    }

    @Test
    @DisplayName("onShortUrlServed resolves and persists region to NewUrlAccessLog")
    void onShortUrlServed_savesRegion() {
        NewUrl newUrl = new NewUrl();
        newUrl.setId(10L);
        newUrl.setShortCode("testCode");

        when(repository.findById(10L)).thenReturn(Optional.of(newUrl));
        when(reverseGeocodingService.reverseGeocode(28.6139, 77.2090))
                .thenReturn(new ReverseGeocodingService.ReverseGeocodeResult("New Delhi", "Delhi", "India", "New Delhi, Delhi, India"));

        ShortUrlServedEvent event = new ShortUrlServedEvent(
                10L,
                "testCode",
                "103.21.244.2",
                "Mozilla/5.0",
                "https://example.com",
                28.6139,
                77.2090,
                15.0
        );

        listener.onShortUrlServed(event);

        ArgumentCaptor<NewUrlAccessLog> captor = ArgumentCaptor.forClass(NewUrlAccessLog.class);
        verify(accessLogRepository, times(1)).save(captor.capture());

        NewUrlAccessLog saved = captor.getValue();
        assertThat(saved.getShortUrlId()).isEqualTo(10L);
        assertThat(saved.getCity()).isEqualTo("New Delhi");
        assertThat(saved.getCountry()).isEqualTo("India");
        assertThat(saved.getRegion()).isEqualTo("New Delhi, Delhi, India");
        assertThat(saved.getLatitude()).isEqualTo(28.6139);
        assertThat(saved.getLongitude()).isEqualTo(77.2090);
    }
}
