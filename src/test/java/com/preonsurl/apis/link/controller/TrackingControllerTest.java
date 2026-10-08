package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.entity.LinkRecipient;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.policy.resolver.ClientIpResolver;
import com.preonsurl.apis.link.policy.resolver.CountryResolver;
import com.preonsurl.apis.link.repository.LinkRecipientRepository;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingControllerTest {

    @Mock
    private LinkRecipientRepository recipientRepository;

    @Mock
    private NewUrlRepository newUrlRepository;

    @Mock
    private NewUrlAccessLogRepository accessLogRepository;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private CountryResolver countryResolver;

    @Mock
    private HttpServletRequest request;

    private TrackingController trackingController;

    @BeforeEach
    void setUp() {
        trackingController = new TrackingController(
                recipientRepository,
                newUrlRepository,
                accessLogRepository,
                clientIpResolver,
                countryResolver,
                null
        );
    }

    @Test
    @DisplayName("trackEmailOpen marks recipient opened and returns 1px PNG image bytes")
    void testTrackEmailOpen() {
        LinkRecipient recipient = new LinkRecipient(100L, "recipient@example.com", "track-abc-123");
        when(recipientRepository.findByTrackingToken("track-abc-123")).thenReturn(Optional.of(recipient));

        ResponseEntity<byte[]> response = trackingController.trackEmailOpen("track-abc-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("image/png");
        assertThat(recipient.isEmailOpened()).isTrue();
        assertThat(recipient.getEmailOpenedAt()).isNotNull();

        verify(recipientRepository).save(recipient);
    }

    @Test
    @DisplayName("trackEmailOpen with request records client IP and location")
    void testTrackEmailOpenWithRequestAndIpLocation() {
        LinkRecipient recipient = new LinkRecipient(100L, "recipient@example.com", "track-abc-123");
        when(recipientRepository.findByTrackingToken("track-abc-123")).thenReturn(Optional.of(recipient));
        when(clientIpResolver.resolveClientIp(request)).thenReturn("203.0.113.195");
        when(countryResolver.resolveCountry(request)).thenReturn("US");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0");

        NewUrl url = new NewUrl();
        url.setId(100L);
        url.setShortCode("sec-url-123");
        when(newUrlRepository.findById(100L)).thenReturn(Optional.of(url));

        ResponseEntity<byte[]> response = trackingController.trackEmailOpen("track-abc-123", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recipient.isEmailOpened()).isTrue();
        assertThat(recipient.getOpenedIp()).isEqualTo("203.0.113.195");
        assertThat(recipient.getOpenedCountry()).isNotNull();

        verify(recipientRepository).save(recipient);

        ArgumentCaptor<NewUrlAccessLog> logCaptor = ArgumentCaptor.forClass(NewUrlAccessLog.class);
        verify(accessLogRepository).save(logCaptor.capture());
        NewUrlAccessLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getShortUrlId()).isEqualTo(100L);
        assertThat(savedLog.getShortCode()).isEqualTo("sec-url-123");
        assertThat(savedLog.getIpAddress()).isEqualTo("203.0.113.195");
        assertThat(savedLog.getReferer()).contains("Email Open");
    }

    @Test
    @DisplayName("trackEmailOpen with short code records open in access logs")
    void testTrackEmailOpenWithShortCode() {
        when(recipientRepository.findByTrackingToken("my-short-code")).thenReturn(Optional.empty());
        when(clientIpResolver.resolveClientIp(request)).thenReturn("198.51.100.42");
        when(countryResolver.resolveCountry(request)).thenReturn("GB");

        NewUrl url = new NewUrl();
        url.setId(200L);
        url.setShortCode("my-short-code");
        when(newUrlRepository.findByPublicId("my-short-code")).thenReturn(Optional.empty());
        when(newUrlRepository.findByShortCode("my-short-code")).thenReturn(Optional.of(url));

        ResponseEntity<byte[]> response = trackingController.trackEmailOpen("my-short-code", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<NewUrlAccessLog> logCaptor = ArgumentCaptor.forClass(NewUrlAccessLog.class);
        verify(accessLogRepository).save(logCaptor.capture());
        NewUrlAccessLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getShortUrlId()).isEqualTo(200L);
        assertThat(savedLog.getIpAddress()).isEqualTo("198.51.100.42");
        assertThat(savedLog.getReferer()).contains("Email Open");
    }

    @Test
    @DisplayName("trackEmailOpen with unknown token still returns 1px PNG without error")
    void testTrackEmailOpenUnknownToken() {
        when(recipientRepository.findByTrackingToken("unknown-token")).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = trackingController.trackEmailOpen("unknown-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("image/png");
    }
}
