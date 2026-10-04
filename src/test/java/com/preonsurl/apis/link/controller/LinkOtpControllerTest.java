package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.link.event.ShortUrlServedEvent;
import com.preonsurl.apis.link.policy.security.SecurityVerificationService;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.service.LinkOtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkOtpControllerTest {

    @Mock
    private LinkOtpService linkOtpService;

    @Mock
    private NewUrlRepository newUrlRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SecurityVerificationService securityVerificationService;

    private LinkOtpController controller;

    @BeforeEach
    void setUp() {
        securityVerificationService = new SecurityVerificationService();
        controller = new LinkOtpController(
                linkOtpService,
                newUrlRepository,
                eventPublisher,
                securityVerificationService
        );
    }

    @Test
    @DisplayName("verifyOtp returns relative path for MIRROR mode without duplicate serve event")
    void testVerifyOtpMirrorMode() {
        NewUrl mirrorUrl = new NewUrl();
        mirrorUrl.setId(201L);
        mirrorUrl.setShortCode("mirror-test");
        mirrorUrl.setNewUrl("http://short.ly/mirror-test");
        mirrorUrl.setOriginalUrl("https://upstream-site.com");
        mirrorUrl.setLinkMode(LinkMode.MIRROR);

        when(newUrlRepository.findByShortCode("mirror-test")).thenReturn(Optional.of(mirrorUrl));
        when(linkOtpService.verifyOtp(mirrorUrl, "alice@example.com", "123456"))
                .thenReturn(new LinkOtpService.VerifyOtpOutcome(true, "Access granted.", "http://short.ly/mirror-test"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        LinkOtpController.VerifyOtpPayload payload = new LinkOtpController.VerifyOtpPayload("mirror-test", "alice@example.com", "123456");

        ResponseEntity<?> response = controller.verifyOtp(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("PREONS_SEC_201=VERIFIED");

        @SuppressWarnings("unchecked")
        ApiResponse<Map<String, String>> body = (ApiResponse<Map<String, String>>) response.getBody();
        assertThat(body.success()).isTrue();

        Map<String, String> data = body.data();
        assertThat(data.get("redirectUrl")).isEqualTo("/mirror-test");
        assertThat(data.get("linkMode")).isEqualTo("MIRROR");

        // Should NOT publish ShortUrlServedEvent at OTP verify time for MIRROR
        verify(eventPublisher, never()).publishEvent(any(ShortUrlServedEvent.class));
    }

    @Test
    @DisplayName("verifyOtp returns relative path for PROXY mode without duplicate serve event")
    void testVerifyOtpProxyMode() {
        NewUrl proxyUrl = new NewUrl();
        proxyUrl.setId(301L);
        proxyUrl.setShortCode("proxy-test");
        proxyUrl.setNewUrl("http://short.ly/proxy-test");
        proxyUrl.setOriginalUrl("https://upstream-site.com");
        proxyUrl.setLinkMode(LinkMode.PROXY);

        when(newUrlRepository.findByShortCode("proxy-test")).thenReturn(Optional.of(proxyUrl));
        when(linkOtpService.verifyOtp(proxyUrl, "bob@example.com", "654321"))
                .thenReturn(new LinkOtpService.VerifyOtpOutcome(true, "Access granted.", "http://short.ly/proxy-test"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        LinkOtpController.VerifyOtpPayload payload = new LinkOtpController.VerifyOtpPayload("/proxy-test", "bob@example.com", "654321");

        ResponseEntity<?> response = controller.verifyOtp(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("PREONS_SEC_301=VERIFIED");

        @SuppressWarnings("unchecked")
        ApiResponse<Map<String, String>> body = (ApiResponse<Map<String, String>>) response.getBody();
        assertThat(body.success()).isTrue();

        Map<String, String> data = body.data();
        assertThat(data.get("redirectUrl")).isEqualTo("/proxy-test");
        assertThat(data.get("linkMode")).isEqualTo("PROXY");

        // Should NOT publish ShortUrlServedEvent at OTP verify time for PROXY
        verify(eventPublisher, never()).publishEvent(any(ShortUrlServedEvent.class));
    }

    @Test
    @DisplayName("verifyOtp returns originalUrl and publishes event for REDIRECT mode")
    void testVerifyOtpRedirectMode() {
        NewUrl redirectUrl = new NewUrl();
        redirectUrl.setId(401L);
        redirectUrl.setShortCode("redirect-test");
        redirectUrl.setNewUrl("http://short.ly/redirect-test");
        redirectUrl.setOriginalUrl("https://destination-site.com/welcome");
        redirectUrl.setLinkMode(LinkMode.REDIRECT);

        when(newUrlRepository.findByShortCode("redirect-test")).thenReturn(Optional.of(redirectUrl));
        when(linkOtpService.verifyOtp(redirectUrl, "carol@example.com", "111222"))
                .thenReturn(new LinkOtpService.VerifyOtpOutcome(true, "Access granted.", "https://destination-site.com/welcome"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");
        LinkOtpController.VerifyOtpPayload payload = new LinkOtpController.VerifyOtpPayload("redirect-test", "carol@example.com", "111222");

        ResponseEntity<?> response = controller.verifyOtp(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("PREONS_SEC_401=VERIFIED");

        @SuppressWarnings("unchecked")
        ApiResponse<Map<String, String>> body = (ApiResponse<Map<String, String>>) response.getBody();
        assertThat(body.success()).isTrue();

        Map<String, String> data = body.data();
        assertThat(data.get("redirectUrl")).isEqualTo("https://destination-site.com/welcome");
        assertThat(data.get("linkMode")).isEqualTo("REDIRECT");

        // MUST publish ShortUrlServedEvent for REDIRECT mode
        verify(eventPublisher, times(1)).publishEvent(any(ShortUrlServedEvent.class));
    }

    @Test
    @DisplayName("verifyOtp returns 401 when OTP code is incorrect")
    void testVerifyOtpInvalidCode() {
        NewUrl entity = new NewUrl();
        entity.setId(501L);
        entity.setShortCode("fail-code");

        when(newUrlRepository.findByShortCode("fail-code")).thenReturn(Optional.of(entity));
        when(linkOtpService.verifyOtp(entity, "user@example.com", "000000"))
                .thenReturn(new LinkOtpService.VerifyOtpOutcome(false, "Invalid verification code.", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        LinkOtpController.VerifyOtpPayload payload = new LinkOtpController.VerifyOtpPayload("fail-code", "user@example.com", "000000");

        ResponseEntity<?> response = controller.verifyOtp(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
