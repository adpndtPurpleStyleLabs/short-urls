package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.dto.LinkRecipientDto;
import com.preonsurl.apis.link.dto.ShareLinkEmailRequest;
import com.preonsurl.apis.link.service.NewUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkShareEmailTest {

    @Mock
    private NewUrlService newUrlService;

    private LinkController linkController;

    @BeforeEach
    void setUp() {
        linkController = new LinkController(newUrlService, null);
    }

    @Test
    @DisplayName("shareTrackedEmail endpoint dispatches email and returns recipient list")
    void testShareTrackedEmailControllerSuccess() {
        AuthenticatedUser user = new AuthenticatedUser(1L, 1L, "testuser");
        ShareLinkEmailRequest req = new ShareLinkEmailRequest("recipient@example.com", "Subject", "Note", "Test User");

        LinkRecipientDto dto = new LinkRecipientDto(
                10L, 100L, "recipient@example.com", "tok123",
                true, null, false, null, null, null, null,
                false, null, false, null, "Email Delivered", null
        );
        when(newUrlService.shareTrackedEmail(eq(1L), eq("pub-123"), any(ShareLinkEmailRequest.class)))
                .thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<LinkRecipientDto>>> response =
                linkController.shareTrackedEmail("pub-123", req, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).email()).isEqualTo("recipient@example.com");
    }

    @Test
    @DisplayName("shareTrackedEmail endpoint returns unauthorized when user is missing")
    void testShareTrackedEmailUnauthorized() {
        ShareLinkEmailRequest req = new ShareLinkEmailRequest("recipient@example.com", "Subject", "Note", "Test User");
        ResponseEntity<ApiResponse<List<LinkRecipientDto>>> response =
                linkController.shareTrackedEmail("pub-123", req, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
