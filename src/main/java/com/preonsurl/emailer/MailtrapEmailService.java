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

import java.nio.charset.StandardCharsets;
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

    @Value("${preonsurl.email.welcome.dashboard-url:https://secure.indexrender.io/console}")
    private String welcomeDashboardUrl;

    @Value("${preonsurl.email.welcome.help-center-url:https://secure.indexrender.io/help}")
    private String welcomeHelpCenterUrl;

    @Value("${preonsurl.email.welcome.support-email:support@indexrender.io}")
    private String welcomeSupportEmail;

    @Value("${preonsurl.email.welcome.docs-url:https://secure.indexrender.io/docs}")
    private String welcomeDocsUrl;

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

    @Override
    public void sendWelcomeEmail(String toEmail, String userName) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("PREONS-EMAILER: Cannot send welcome email because recipient email is blank.");
            return;
        }

        String safeName = (userName != null && !userName.isBlank()) ? userName.trim() : "Member";
        String subject = "Your URL is Secured - Welcome to SecureURL";

        String htmlContent = buildWelcomeEmailHtml(safeName);
        String textContent = buildWelcomeEmailPlainText(safeName);

        log.info("PREONS-EMAILER [Mailtrap API] Dispatching welcome email to '{}' (userName: '{}')", toEmail, safeName);

        if (apiToken == null || apiToken.isBlank()) {
            log.info("PREONS-EMAILER: Mailtrap API token not configured. Welcome email logged for '{}'.", toEmail);
            return;
        }

        try {
            MailtrapClient client = getClient();
            if (client == null) {
                log.info("PREONS-EMAILER: MailtrapClient unavailable. Welcome email logged for '{}'.", toEmail);
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
                    .category("Welcome Email")
                    .build();

            SendResponse response = client.send(mail);
            log.info("PREONS-EMAILER: Welcome email successfully dispatched via Mailtrap API to '{}' (response: {})",
                    toEmail, response);
        } catch (Exception e) {
            log.warn("PREONS-EMAILER: Mailtrap API welcome email dispatch to '{}' encountered: {}",
                    toEmail, e.getMessage());
        }
    }

    private String welcomeTemplateHtmlCache = null;

    private synchronized String getWelcomeTemplateHtml() {
        if (welcomeTemplateHtmlCache != null) {
            return welcomeTemplateHtmlCache;
        }
        try (var is = getClass().getResourceAsStream("/templates/email/welcome.html")) {
            if (is != null) {
                welcomeTemplateHtmlCache = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return welcomeTemplateHtmlCache;
            }
        } catch (Exception e) {
            log.warn("Could not load /templates/email/welcome.html from classpath: {}", e.getMessage());
        }
        return null;
    }

    private String buildWelcomeEmailHtml(String userName) {
        String template = getWelcomeTemplateHtml();
        String dashboardUrl = (welcomeDashboardUrl != null && !welcomeDashboardUrl.isBlank())
                ? welcomeDashboardUrl : "https://secure.indexrender.io/console";
        String helpCenterUrl = (welcomeHelpCenterUrl != null && !welcomeHelpCenterUrl.isBlank())
                ? welcomeHelpCenterUrl : "https://secure.indexrender.io/help";
        String supportEmail = (welcomeSupportEmail != null && !welcomeSupportEmail.isBlank())
                ? welcomeSupportEmail : "support@indexrender.io";
        String docsUrl = (welcomeDocsUrl != null && !welcomeDocsUrl.isBlank())
                ? welcomeDocsUrl : "https://secure.indexrender.io/docs";

        if (template != null) {
            return template
                    .replace("{{userName}}", userName)
                    .replace("{{dashboardUrl}}", dashboardUrl)
                    .replace("{{helpCenterUrl}}", helpCenterUrl)
                    .replace("{{supportEmail}}", supportEmail)
                    .replace("{{docsUrl}}", docsUrl);
        }

        return buildFallbackWelcomeHtml(userName, dashboardUrl, helpCenterUrl, supportEmail, docsUrl);
    }

    private String buildFallbackWelcomeHtml(String userName, String dashboardUrl, String helpCenterUrl, String supportEmail, String docsUrl) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><title>Your URL is Secured - SecureURL</title></head>
            <body>
                <p>Hello %s,</p>
                <p><strong>Your URL is secured. 🔒</strong></p>
                <p>Welcome to SecureURL — your links now have built-in control and protection.</p>
                <p><a href="%s">Open SecureURL</a></p>
                <p>Need help? Visit our <a href="%s">Help Center</a> or email <a href="mailto:%s">%s</a>.</p>
                <p><a href="%s">Dashboard</a> | <a href="%s">Docs</a> | <a href="%s">Help</a></p>
            </body>
            </html>
            """.formatted(userName, dashboardUrl, helpCenterUrl, supportEmail, supportEmail, dashboardUrl, docsUrl, helpCenterUrl);
    }

    private String buildWelcomeEmailPlainText(String userName) {
        String dashboardUrl = (welcomeDashboardUrl != null && !welcomeDashboardUrl.isBlank())
                ? welcomeDashboardUrl : "https://secure.indexrender.io/console";
        String helpCenterUrl = (welcomeHelpCenterUrl != null && !welcomeHelpCenterUrl.isBlank())
                ? welcomeHelpCenterUrl : "https://secure.indexrender.io/help";
        String supportEmail = (welcomeSupportEmail != null && !welcomeSupportEmail.isBlank())
                ? welcomeSupportEmail : "support@indexrender.io";
        String docsUrl = (welcomeDocsUrl != null && !welcomeDocsUrl.isBlank())
                ? welcomeDocsUrl : "https://secure.indexrender.io/docs";

        return """
            Hello %s,

            Your URL is secured. 🔒
            Welcome to SecureURL — your links now have built-in control and protection.

            Create short, branded URLs and control how they are accessed, when they expire, and where your visitors go.

            Open SecureURL: %s

            Need help getting started?
            Visit our Help Center: %s
            Or contact us at: %s
            Documentation: %s

            Keep your links under control,
            The SecureURL Team
            """.formatted(userName, dashboardUrl, helpCenterUrl, supportEmail, docsUrl);
    }

    @Override
    public void sendPaymentSuccessEmail(String toEmail, String userName, String invoiceNumber, String amount, String plan, String date, String paymentId) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("PREONS-EMAILER: Cannot send payment success email because recipient email is blank.");
            return;
        }

        String safeName = (userName != null && !userName.isBlank()) ? userName.trim() : "Member";
        String subject = "Payment Confirmed - Welcome to SecureURL PRO! (Invoice #" + invoiceNumber + ")";
        String dashboardUrl = (welcomeDashboardUrl != null && !welcomeDashboardUrl.isBlank())
                ? welcomeDashboardUrl : "https://secure.indexrender.io/console";
        String supportEmail = (welcomeSupportEmail != null && !welcomeSupportEmail.isBlank())
                ? welcomeSupportEmail : "support@indexrender.io";

        String htmlContent = buildPaymentSuccessHtml(safeName, invoiceNumber, amount, plan, date, paymentId, dashboardUrl, supportEmail);
        String textContent = """
            Hello %s,

            Thank you for upgrading to SecureURL PRO!
            Your payment of %s has been confirmed.

            Transaction Details:
            - Invoice Number: %s
            - Payment ID: %s
            - Plan: %s (Lifetime License)
            - Date: %s
            - Status: PAID

            Access your console: %s
            Support: %s

            Best regards,
            The SecureURL Team
            """.formatted(safeName, amount, invoiceNumber, paymentId, plan, date, dashboardUrl, supportEmail);

        log.info("PREONS-EMAILER: Dispatching payment success email to '{}' for invoice '{}'", toEmail, invoiceNumber);

        if (apiToken == null || apiToken.isBlank()) {
            log.info("PREONS-EMAILER: Mailtrap API token not configured. Payment success email logged for '{}'.", toEmail);
            return;
        }

        try {
            MailtrapClient client = getClient();
            if (client == null) {
                log.info("PREONS-EMAILER: MailtrapClient unavailable. Payment success email logged for '{}'.", toEmail);
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
                    .category("Payment Receipt")
                    .build();

            SendResponse response = client.send(mail);
            log.info("PREONS-EMAILER: Payment success email dispatched to '{}' (response: {})", toEmail, response);
        } catch (Exception e) {
            log.warn("PREONS-EMAILER: Mailtrap API payment success email dispatch to '{}' encountered: {}", toEmail, e.getMessage());
        }
    }

    @Override
    public void sendPaymentFailedEmail(String toEmail, String userName, String amount, String plan, String date, String reason) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("PREONS-EMAILER: Cannot send payment failure email because recipient email is blank.");
            return;
        }

        String safeName = (userName != null && !userName.isBlank()) ? userName.trim() : "Member";
        String subject = "Payment Unsuccessful - SecureURL PRO Plan";
        String dashboardUrl = (welcomeDashboardUrl != null && !welcomeDashboardUrl.isBlank())
                ? welcomeDashboardUrl : "https://secure.indexrender.io/console";
        String supportEmail = (welcomeSupportEmail != null && !welcomeSupportEmail.isBlank())
                ? welcomeSupportEmail : "support@indexrender.io";

        String htmlContent = buildPaymentFailedHtml(safeName, amount, plan, date, reason, dashboardUrl, supportEmail);
        String textContent = """
            Hello %s,

            We were unable to complete your payment of %s for the %s.
            Reason: %s
            Date: %s

            You can retry the transaction anytime from your dashboard: %s
            Support: %s

            Best regards,
            The SecureURL Team
            """.formatted(safeName, amount, plan, reason, date, dashboardUrl, supportEmail);

        log.info("PREONS-EMAILER: Dispatching payment failed email to '{}'", toEmail);

        if (apiToken == null || apiToken.isBlank()) {
            log.info("PREONS-EMAILER: Mailtrap API token not configured. Payment failed email logged for '{}'.", toEmail);
            return;
        }

        try {
            MailtrapClient client = getClient();
            if (client == null) {
                log.info("PREONS-EMAILER: MailtrapClient unavailable. Payment failed email logged for '{}'.", toEmail);
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
                    .category("Payment Alert")
                    .build();

            SendResponse response = client.send(mail);
            log.info("PREONS-EMAILER: Payment failed email dispatched to '{}' (response: {})", toEmail, response);
        } catch (Exception e) {
            log.warn("PREONS-EMAILER: Mailtrap API payment failed email dispatch to '{}' encountered: {}", toEmail, e.getMessage());
        }
    }

    private String buildPaymentSuccessHtml(String userName, String invoiceNumber, String amount, String plan, String date, String paymentId, String dashboardUrl, String supportEmail) {
        if (templateEngine != null) {
            try {
                Context context = new Context();
                context.setVariable("userName", userName);
                context.setVariable("invoiceNumber", invoiceNumber);
                context.setVariable("amount", amount);
                context.setVariable("plan", plan);
                context.setVariable("date", date);
                context.setVariable("paymentId", paymentId);
                context.setVariable("dashboardUrl", dashboardUrl);
                context.setVariable("supportEmail", supportEmail);
                return templateEngine.process("email/payment-success", context);
            } catch (Exception e) {
                log.warn("Thymeleaf payment-success template failed: {}, using fallback", e.getMessage());
            }
        }
        return """
            <!DOCTYPE html><html><body>
            <h2>Payment Confirmed</h2>
            <p>Hello %s, your payment of <strong>%s</strong> for SecureURL PRO has been processed.</p>
            <p>Invoice: %s | Payment ID: %s | Date: %s</p>
            <p><a href="%s">Open Dashboard</a></p>
            </body></html>
            """.formatted(userName, amount, invoiceNumber, paymentId, date, dashboardUrl);
    }

    private String buildPaymentFailedHtml(String userName, String amount, String plan, String date, String reason, String dashboardUrl, String supportEmail) {
        if (templateEngine != null) {
            try {
                Context context = new Context();
                context.setVariable("userName", userName);
                context.setVariable("amount", amount);
                context.setVariable("plan", plan);
                context.setVariable("date", date);
                context.setVariable("reason", reason != null ? reason : "Payment provider declined the transaction.");
                context.setVariable("dashboardUrl", dashboardUrl);
                context.setVariable("supportEmail", supportEmail);
                return templateEngine.process("email/payment-failed", context);
            } catch (Exception e) {
                log.warn("Thymeleaf payment-failed template failed: {}, using fallback", e.getMessage());
            }
        }
        return """
            <!DOCTYPE html><html><body>
            <h2>Payment Unsuccessful</h2>
            <p>Hello %s, we could not process your payment of <strong>%s</strong>.</p>
            <p>Reason: %s</p>
            <p><a href="%s">Retry Payment</a></p>
            </body></html>
            """.formatted(userName, amount, reason, dashboardUrl);
    }
}
