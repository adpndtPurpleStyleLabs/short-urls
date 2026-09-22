package com.preonsurl.emailer;

import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig;
import io.mailtrap.factory.MailtrapClientFactory;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;
import io.mailtrap.model.response.emails.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

/**
 * Mailtrap API email service implementation for PreonsURL using official mailtrap-java SDK.
 * Renders the PREONS-style email verification template and dispatches codes via Mailtrap API.
 */
@Service
public class MailtrapEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(MailtrapEmailService.class);

    @Value("${mailtrap.api-token:laudalasan}")
    private String apiToken;

    @Value("${mailtrap.sender-email:hello@madxglobaltech.com}")
    private String senderEmail;

    @Value("${mailtrap.sender-name:PruneUrl}")
    private String senderName;

    private MailtrapClient mailtrapClient;
    private final TemplateEngine templateEngine;

    @Autowired
    public MailtrapEmailService(
            @Autowired(required = false) MailtrapClient mailtrapClient,
            @Autowired(required = false) TemplateEngine templateEngine) {
        this.mailtrapClient = mailtrapClient;
        this.templateEngine = templateEngine;
    }

    public MailtrapEmailService() {
        this(null, null);
    }

    public MailtrapEmailService(MailtrapClient mailtrapClient) {
        this(mailtrapClient, null);
    }

    private synchronized MailtrapClient getClient() {
        if (mailtrapClient != null) {
            return mailtrapClient;
        }
        if (apiToken == null || apiToken.isBlank()) {
            return null;
        }
        MailtrapConfig config = new MailtrapConfig.Builder()
                .token(apiToken.trim())
                .build();
        this.mailtrapClient = MailtrapClientFactory.createMailtrapClient(config);
        return this.mailtrapClient;
    }

    @Override
    public void sendVerificationCode(String toEmail, String code, String recipientName) {
        String safeName = (recipientName != null && !recipientName.isBlank()) ? recipientName.trim() : "Member";
        String subject = "Your verification code: " + code;

        String htmlContent = buildVerificationEmailHtml(safeName, code);
        String textContent = buildVerificationEmailPlainText(safeName, code);

        log.info("PREONS-EMAILER [Mailtrap API] Dispatching verification code to '{}': code='{}'", toEmail, code);

        if (apiToken == null || apiToken.isBlank()) {
            log.info("PREONS-EMAILER: Mailtrap API token not configured. Verification code '{}' logged for '{}'.", code, toEmail);
            return;
        }

        try {
            MailtrapClient client = getClient();
            if (client == null) {
                log.info("PREONS-EMAILER: MailtrapClient unavailable. Verification code '{}' logged for '{}'.", code, toEmail);
                return;
            }

            Address from = new Address(senderEmail, senderName);
            Address to = new Address(toEmail, safeName);

            MailtrapMail mail = MailtrapMail.builder()
                    .from(from)
                    .to(List.of(to))
                    .subject(subject)
                    .html(htmlContent)
                    .text(textContent)
                    .category("Email Verification")
                    .build();

            SendResponse response = client.send(mail);
            log.info("PREONS-EMAILER: Verification email successfully dispatched via Mailtrap API to '{}' (response: {})",
                    toEmail, response);
        } catch (Exception e) {
            log.warn("PREONS-EMAILER: Mailtrap API dispatch to '{}' encountered: {}. Fallback verification code: '{}'",
                    toEmail, e.getMessage(), code);
            // Non-blocking fallback so registration/resend flow remains operational even with test/dummy API token
        }
    }

    private String buildVerificationEmailHtml(String recipientName, String code) {
        String formattedCode = (code != null && code.length() == 6)
                ? String.join(" ", code.split(""))
                : (code != null ? code : "");

        if (templateEngine != null) {
            try {
                Context context = new Context();
                context.setVariable("brandName", "PREONS");
                context.setVariable("code", formattedCode);
                context.setVariable("expiryMinutes", "15");
                context.setVariable("copyrightName", "Preons");
                context.setVariable("privacyUrl", "https://preonsurl.com/privacy");
                context.setVariable("termsUrl", "https://preonsurl.com/terms");
                return templateEngine.process("email-verification", context);
            } catch (Exception e) {
                log.warn("Thymeleaf email template rendering failed, falling back to inline template: {}", e.getMessage());
            }
        }

        return buildFallbackEmailHtml(formattedCode);
    }

    private String buildFallbackEmailHtml(String formattedCode) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Verification Code</title>
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        background-color: #ffffff;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        color: #000000;
                    }
                    .container {
                        max-width: 580px;
                        margin: 40px auto;
                        padding: 20px;
                        background-color: #ffffff;
                        text-align: center;
                    }
                    .brand {
                        font-size: 44px;
                        font-weight: 800;
                        letter-spacing: 0.18em;
                        color: #EA282E;
                        text-transform: uppercase;
                        margin-bottom: 52px;
                        line-height: 1;
                    }
                    .brand-reg {
                        font-size: 22px;
                        vertical-align: top;
                        margin-left: 2px;
                        font-weight: 700;
                    }
                    .instruction {
                        font-size: 17px;
                        line-height: 24px;
                        color: #222222;
                        margin-bottom: 22px;
                    }
                    .code-display {
                        font-size: 34px;
                        font-weight: 800;
                        letter-spacing: 0.35em;
                        color: #000000;
                        margin-bottom: 24px;
                        margin-left: 0.35em;
                        line-height: 1.2;
                    }
                    .expiry-note {
                        font-size: 15px;
                        line-height: 22px;
                        color: #374151;
                        margin-bottom: 72px;
                    }
                    .footer-copy {
                        font-size: 13px;
                        color: #4b5563;
                        margin-bottom: 16px;
                    }
                    .footer-links a {
                        font-size: 13px;
                        color: #2563eb;
                        text-decoration: none;
                        margin: 0 10px;
                    }
                    .footer-links a:hover {
                        text-decoration: underline;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="brand">LUDIC<span class="brand-reg">&reg;</span></div>
                    <p class="instruction">Your verification code:</p>
                    <div class="code-display">%s</div>
                    <p class="expiry-note">This code can only be used once. It expires in 15 minutes.</p>
                    <p class="footer-copy">&copy; Ludic</p>
                    <div class="footer-links">
                        <a href="https://preonsurl.com/privacy">Privacy policy</a>
                        <a href="https://preonsurl.com/terms">Terms of service</a>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(formattedCode);
    }

    private String buildVerificationEmailPlainText(String recipientName, String code) {
        String formattedCode = (code != null && code.length() == 6)
                ? String.join(" ", code.split(""))
                : (code != null ? code : "");

        return """
            LUDIC(R)

            Your verification code:

            %s

            This code can only be used once. It expires in 15 minutes.

            (C) Ludic
            Privacy policy: https://preonsurl.com/privacy
            Terms of service: https://preonsurl.com/terms
            """.formatted(formattedCode);
    }
}
