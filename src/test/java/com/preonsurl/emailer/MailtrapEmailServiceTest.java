package com.preonsurl.emailer;

import io.mailtrap.client.MailtrapClient;
import io.mailtrap.model.request.emails.MailtrapMail;
import io.mailtrap.model.response.emails.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MailtrapEmailServiceTest {

    private MailtrapClient mailtrapClient;
    private MailtrapEmailService emailService;

    @BeforeEach
    void setUp() {
        mailtrapClient = mock(MailtrapClient.class);
        emailService = new MailtrapEmailService(mailtrapClient);
        ReflectionTestUtils.setField(emailService, "apiToken", "laudalasan");
        ReflectionTestUtils.setField(emailService, "senderEmail", "hello@madxglobaltech.com");
        ReflectionTestUtils.setField(emailService, "senderName", "PruneUrl");
    }

    @Test
    void sendVerificationCode_withValidMailtrapConfig_callsClientSend() {
        SendResponse mockResponse = mock(SendResponse.class);
        when(mailtrapClient.send(any(MailtrapMail.class))).thenReturn(mockResponse);

        emailService.sendVerificationCode("alex@example.com", "849201", "Alexander Hamilton");

        ArgumentCaptor<MailtrapMail> mailCaptor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mailtrapClient, times(1)).send(mailCaptor.capture());

        MailtrapMail sentMail = mailCaptor.getValue();
        assertEquals("hello@madxglobaltech.com", sentMail.getFrom().getEmail());
        assertEquals("PruneUrl", sentMail.getFrom().getName());
        assertEquals(1, sentMail.getTo().size());
        assertEquals("alex@example.com", sentMail.getTo().get(0).getEmail());
        assertTrue(sentMail.getSubject().contains("849201"));
        assertTrue(sentMail.getText().contains("8 4 9 2 0 1"));
        assertTrue(sentMail.getHtml().contains("LUDIC"));
        assertTrue(sentMail.getHtml().contains("Your verification code:"));
        assertTrue(sentMail.getHtml().contains("8 4 9 2 0 1"));
        assertTrue(sentMail.getHtml().contains("Privacy policy"));
        assertTrue(sentMail.getHtml().contains("Terms of service"));
    }

    @Test
    void sendVerificationCode_whenApiTokenBlank_doesNotCallClient() {
        ReflectionTestUtils.setField(emailService, "apiToken", "");

        assertDoesNotThrow(() ->
                emailService.sendVerificationCode("alex@example.com", "849201", "Alexander Hamilton")
        );

        verify(mailtrapClient, never()).send(any(MailtrapMail.class));
    }

    @Test
    void sendVerificationCode_whenClientThrows_handlesGracefullyWithoutException() {
        doThrow(new RuntimeException("Mailtrap API 401 Unauthorized")).when(mailtrapClient).send(any(MailtrapMail.class));

        assertDoesNotThrow(() ->
                emailService.sendVerificationCode("alex@example.com", "849201", "Alexander Hamilton")
        );

        verify(mailtrapClient, times(1)).send(any(MailtrapMail.class));
    }

    @Test
    void sendWelcomeEmail_withValidMailtrapConfig_callsClientSendWithVariablesSubstituted() {
        SendResponse mockResponse = mock(SendResponse.class);
        when(mailtrapClient.send(any(MailtrapMail.class))).thenReturn(mockResponse);

        emailService.sendWelcomeEmail("aditya@example.com", "Aditya");

        ArgumentCaptor<MailtrapMail> mailCaptor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mailtrapClient, times(1)).send(mailCaptor.capture());

        MailtrapMail sentMail = mailCaptor.getValue();
        assertEquals("hello@madxglobaltech.com", sentMail.getFrom().getEmail());
        assertEquals("PruneUrl", sentMail.getFrom().getName());
        assertEquals(1, sentMail.getTo().size());
        assertEquals("aditya@example.com", sentMail.getTo().get(0).getEmail());
        assertEquals("Aditya", sentMail.getTo().get(0).getName());
        assertTrue(sentMail.getSubject().contains("Your URL is Secured"));

        String html = sentMail.getHtml();
        assertNotNull(html);
        assertTrue(html.contains("Aditya"), "HTML should contain replaced userName");
        assertTrue(html.contains("https://secure.indexrender.io/console"), "HTML should contain replaced dashboardUrl");
        assertTrue(html.contains("https://secure.indexrender.io/help"), "HTML should contain replaced helpCenterUrl");
        assertTrue(html.contains("support@indexrender.io"), "HTML should contain replaced supportEmail");
        assertTrue(html.contains("https://secure.indexrender.io/docs"), "HTML should contain replaced docsUrl");

        assertFalse(html.contains("{{userName}}"), "HTML should not contain unreplaced {{userName}}");
        assertFalse(html.contains("{{dashboardUrl}}"), "HTML should not contain unreplaced {{dashboardUrl}}");
        assertFalse(html.contains("{{helpCenterUrl}}"), "HTML should not contain unreplaced {{helpCenterUrl}}");
        assertFalse(html.contains("{{supportEmail}}"), "HTML should not contain unreplaced {{supportEmail}}");
        assertFalse(html.contains("{{docsUrl}}"), "HTML should not contain unreplaced {{docsUrl}}");

        String text = sentMail.getText();
        assertNotNull(text);
        assertTrue(text.contains("Aditya"));
        assertTrue(text.contains("https://secure.indexrender.io/console"));
        assertTrue(text.contains("https://secure.indexrender.io/help"));
        assertTrue(text.contains("support@indexrender.io"));
        assertTrue(text.contains("https://secure.indexrender.io/docs"));
    }

    @Test
    void sendWelcomeEmail_whenApiTokenBlank_doesNotCallClient() {
        ReflectionTestUtils.setField(emailService, "apiToken", "");

        assertDoesNotThrow(() ->
                emailService.sendWelcomeEmail("aditya@example.com", "Aditya")
        );

        verify(mailtrapClient, never()).send(any(MailtrapMail.class));
    }

    @Test
    void sendWelcomeEmail_whenClientThrows_handlesGracefullyWithoutException() {
        doThrow(new RuntimeException("Mailtrap API 500 Server Error")).when(mailtrapClient).send(any(MailtrapMail.class));

        assertDoesNotThrow(() ->
                emailService.sendWelcomeEmail("aditya@example.com", "Aditya")
        );

        verify(mailtrapClient, times(1)).send(any(MailtrapMail.class));
    }

    @Test
    void sendWelcomeEmail_whenToEmailBlank_doesNothing() {
        assertDoesNotThrow(() ->
                emailService.sendWelcomeEmail("", "Aditya")
        );

        verify(mailtrapClient, never()).send(any(MailtrapMail.class));
    }
}
