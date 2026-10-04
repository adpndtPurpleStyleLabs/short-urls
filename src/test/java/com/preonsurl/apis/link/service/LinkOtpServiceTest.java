package com.preonsurl.apis.link.service;

import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.LinkRecipient;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.repository.AccessPolicyRepository;
import com.preonsurl.apis.link.repository.LinkRecipientRepository;
import com.preonsurl.emailer.EmailService;
import com.preonsurl.apis.link.enums.AccessPolicyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkOtpServiceTest {

    @Mock
    private LinkRecipientRepository recipientRepository;

    @Mock
    private AccessPolicyRepository accessPolicyRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private LinkOtpService linkOtpService;

    private NewUrl newUrl;
    private AccessPolicy accessPolicy;

    @BeforeEach
    void setUp() {
        linkOtpService = new LinkOtpService(recipientRepository, accessPolicyRepository, emailService, passwordEncoder);

        newUrl = new NewUrl();
        newUrl.setId(100L);
        newUrl.setShortCode("otp123");
        newUrl.setNewUrl("http://short.ly/otp123");
        newUrl.setOriginalUrl("https://destination.example.com/secret");

        accessPolicy = new AccessPolicy();
        accessPolicy.setShortUrlId(100L);
        accessPolicy.setMode(AccessPolicyMode.SECURED);
        accessPolicy.setOtpEnabled(true);
        accessPolicy.setOtpEmails("alice@example.com, bob@example.com");
    }

    @Test
    @DisplayName("requestOtp fails when email is not in authorized list")
    void testRequestOtpUnauthorizedEmail() {
        when(accessPolicyRepository.findByShortUrlId(100L)).thenReturn(Optional.of(accessPolicy));

        LinkOtpService.RequestOtpOutcome outcome = linkOtpService.requestOtp(newUrl, "unauthorized@example.com");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("not authorized");
    }

    @Test
    @DisplayName("requestOtp succeeds for authorized email and updates recipient")
    void testRequestOtpAuthorizedEmail() {
        when(accessPolicyRepository.findByShortUrlId(100L)).thenReturn(Optional.of(accessPolicy));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-otp");
        when(recipientRepository.findByShortUrlIdAndEmailIgnoreCase(100L, "alice@example.com")).thenReturn(Optional.empty());
        when(recipientRepository.save(any(LinkRecipient.class))).thenAnswer(inv -> inv.getArgument(0));

        LinkOtpService.RequestOtpOutcome outcome = linkOtpService.requestOtp(newUrl, "alice@example.com");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.message()).contains("sent");

        verify(emailService).sendLinkAccessOtp(eq("alice@example.com"), anyString(), eq("http://short.ly/otp123"));
        verify(recipientRepository).save(any(LinkRecipient.class));
    }

    @Test
    @DisplayName("verifyOtp fails when OTP code is invalid")
    void testVerifyOtpInvalid() {
        LinkRecipient recipient = new LinkRecipient(100L, "alice@example.com", "token123");
        recipient.setOtpRequested(true);
        recipient.setOtpCodeHash("hashed-otp");
        recipient.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(recipientRepository.findByShortUrlIdAndEmailIgnoreCase(100L, "alice@example.com")).thenReturn(Optional.of(recipient));
        when(passwordEncoder.matches("999999", "hashed-otp")).thenReturn(false);

        LinkOtpService.VerifyOtpOutcome outcome = linkOtpService.verifyOtp(newUrl, "alice@example.com", "999999");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("Invalid");
    }

    @Test
    @DisplayName("verifyOtp succeeds, records pageOpened, and returns destination URL")
    void testVerifyOtpSuccess() {
        LinkRecipient recipient = new LinkRecipient(100L, "alice@example.com", "token123");
        recipient.setOtpRequested(true);
        recipient.setOtpCodeHash("hashed-otp");
        recipient.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(recipientRepository.findByShortUrlIdAndEmailIgnoreCase(100L, "alice@example.com")).thenReturn(Optional.of(recipient));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(recipientRepository.save(any(LinkRecipient.class))).thenAnswer(inv -> inv.getArgument(0));

        LinkOtpService.VerifyOtpOutcome outcome = linkOtpService.verifyOtp(newUrl, "alice@example.com", "123456");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.redirectUrl()).isEqualTo("https://destination.example.com/secret");
        assertThat(recipient.isPageOpened()).isTrue();
        assertThat(recipient.getPageOpenedAt()).isNotNull();

        verify(recipientRepository).save(recipient);
    }

    @Test
    @DisplayName("verifyOtp returns short newUrl when linkMode is MIRROR")
    void testVerifyOtpSuccessMirrorMode() {
        newUrl.setLinkMode(com.preonsurl.apis.link.enums.LinkMode.MIRROR);
        LinkRecipient recipient = new LinkRecipient(100L, "alice@example.com", "token123");
        recipient.setOtpRequested(true);
        recipient.setOtpCodeHash("hashed-otp");
        recipient.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(recipientRepository.findByShortUrlIdAndEmailIgnoreCase(100L, "alice@example.com")).thenReturn(Optional.of(recipient));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(recipientRepository.save(any(LinkRecipient.class))).thenAnswer(inv -> inv.getArgument(0));

        LinkOtpService.VerifyOtpOutcome outcome = linkOtpService.verifyOtp(newUrl, "alice@example.com", "123456");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.redirectUrl()).isEqualTo("http://short.ly/otp123");
    }

    @Test
    @DisplayName("verifyOtp returns short newUrl when linkMode is PROXY")
    void testVerifyOtpSuccessProxyMode() {
        newUrl.setLinkMode(com.preonsurl.apis.link.enums.LinkMode.PROXY);
        LinkRecipient recipient = new LinkRecipient(100L, "alice@example.com", "token123");
        recipient.setOtpRequested(true);
        recipient.setOtpCodeHash("hashed-otp");
        recipient.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(recipientRepository.findByShortUrlIdAndEmailIgnoreCase(100L, "alice@example.com")).thenReturn(Optional.of(recipient));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(recipientRepository.save(any(LinkRecipient.class))).thenAnswer(inv -> inv.getArgument(0));

        LinkOtpService.VerifyOtpOutcome outcome = linkOtpService.verifyOtp(newUrl, "alice@example.com", "123456");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.redirectUrl()).isEqualTo("http://short.ly/otp123");
    }
}
