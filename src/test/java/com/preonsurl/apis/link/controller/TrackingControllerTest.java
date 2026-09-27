package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.entity.LinkRecipient;
import com.preonsurl.apis.link.repository.LinkRecipientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingControllerTest {

    @Mock
    private LinkRecipientRepository recipientRepository;

    private TrackingController trackingController;

    @BeforeEach
    void setUp() {
        trackingController = new TrackingController(recipientRepository);
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
    @DisplayName("trackEmailOpen with unknown token still returns 1px PNG without error")
    void testTrackEmailOpenUnknownToken() {
        when(recipientRepository.findByTrackingToken("unknown-token")).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = trackingController.trackEmailOpen("unknown-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("image/png");
    }
}
