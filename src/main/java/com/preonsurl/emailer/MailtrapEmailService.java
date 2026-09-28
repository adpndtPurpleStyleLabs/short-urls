package com.preonsurl.emailer;

import com.preonsurl.coreconfig.constants.CoreConfigKeys;
import com.preonsurl.coreconfig.service.CoreConfigService;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig;
import io.mailtrap.factory.MailtrapClientFactory;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;
import io.mailtrap.model.response.emails.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final String apiToken;
    private final String senderEmail;
    private final String senderName;
    private final String welcomeDashboardUrl;
    private final String welcomeHelpCenterUrl;
    private final String welcomeSupportEmail;
    private final String welcomeDocsUrl;

    private MailtrapClient mailtrapClient;
    private final TemplateEngine templateEngine;

    @Autowired
    public MailtrapEmailService(
            @Autowired(required = false) MailtrapClient mailtrapClient,
            @Autowired(required = false) TemplateEngine templateEngine,
            CoreConfigService coreConfigService) {
        String appUrl = coreConfigService.get(CoreConfigKeys.App.APP_URL);

        this.mailtrapClient = mailtrapClient;
        this.templateEngine = templateEngine;
        this.apiToken = coreConfigService.get(CoreConfigKeys.Email.API_KEY);
        this.senderEmail = coreConfigService.get(CoreConfigKeys.Email.EMAIL);
        this.senderName = coreConfigService.get(CoreConfigKeys.Email.NAME);
        this.welcomeDashboardUrl = appUrl + coreConfigService.get(CoreConfigKeys.Endpoint.CONSOLE);
        this.welcomeHelpCenterUrl = appUrl + coreConfigService.get(CoreConfigKeys.Endpoint.HELP);
        this.welcomeDocsUrl = appUrl + coreConfigService.get(CoreConfigKeys.Endpoint.DOC);
        this.welcomeSupportEmail = coreConfigService.get(CoreConfigKeys.Email.SUPPORT_EMAIL);
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

    @Override
    public void sendSecuredLinkInvitation(String toEmail, String linkUrl, String trackingPixelUrl) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("PREONS-EMAILER: Cannot send secured link invitation because recipient email is blank.");
            return;
        }

        String subject = "You've received a secure link";
        String htmlContent = buildInvitationEmailHtml(toEmail, linkUrl, trackingPixelUrl);
        String textContent = "You have received a secured link on PreonsURL:\n\n" + linkUrl +
                "\n\nAn email OTP verification will be required upon opening.";

        log.info("PREONS-EMAILER [Mailtrap API] Dispatching secured link invitation to '{}' (link: '{}')", toEmail, linkUrl);

        if (apiToken == null || apiToken.isBlank()) {
            log.info("PREONS-EMAILER: Mailtrap API token not configured. Invitation for '{}' logged for '{}'.", linkUrl, toEmail);
            return;
        }

        try {
            MailtrapClient client = getClient();
            if (client == null) {
                log.info("PREONS-EMAILER: MailtrapClient unavailable. Invitation for '{}' logged for '{}'.", linkUrl, toEmail);
                return;
            }

            Address from = new Address(senderEmail, senderName);
            Address to = new Address(toEmail, toEmail);

            MailtrapMail mail = MailtrapMail.builder()
                    .from(from)
                    .to(List.of(to))
                    .subject(subject)
                    .html(htmlContent)
                    .text(textContent)
                    .category("Secured Link Invitation")
                    .build();

            SendResponse response = client.send(mail);
            log.info("PREONS-EMAILER: Secured link invitation successfully sent to '{}' (response: {})", toEmail, response);
        } catch (Exception e) {
            log.warn("PREONS-EMAILER: Failed to dispatch invitation to '{}': {}", toEmail, e.getMessage());
        }
    }

    private String buildInvitationEmailHtml(
            String toEmail,
            String linkUrl,
            String trackingPixelUrl
    ) {
        return """
            <!DOCTYPE html>
            <html lang="en">

            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Secure Link Access</title>

                <style>
                    /* ================================
                       Base / Email Reset
                       ================================ */

                    html,
                    body {
                        margin: 0 !important;
                        padding: 0 !important;
                        width: 100%% !important;
                        min-width: 100%% !important;
                        background-color: #ffffff;
                    }

                    body {
                        font-family:
                            -apple-system,
                            BlinkMacSystemFont,
                            "Segoe UI",
                            Roboto,
                            Helvetica,
                            Arial,
                            sans-serif;

                        color: #000000;
                        -webkit-font-smoothing: antialiased;
                        -moz-osx-font-smoothing: grayscale;
                        text-rendering: optimizeLegibility;
                    }

                    body,
                    table,
                    td,
                    p,
                    a {
                        -webkit-text-size-adjust: 100%%;
                        -ms-text-size-adjust: 100%%;
                    }

                    table,
                    td {
                        mso-table-lspace: 0pt;
                        mso-table-rspace: 0pt;
                    }

                    img {
                        -ms-interpolation-mode: bicubic;
                        border: 0;
                        outline: none;
                        text-decoration: none;
                        display: block;
                        height: auto;
                        line-height: 100%%;
                    }

                    table {
                        border-collapse: collapse !important;
                        border-spacing: 0;
                    }

                    a {
                        color: inherit;
                        text-decoration: none;
                    }

                    /* ================================
                       Main Page
                       ================================ */

                    .page {
                        width: 100%%;
                        background-color: #ffffff;
                        padding: 48px 20px;
                    }

                    /* ================================
                       Email Container
                       ================================ */

                    .container {
                        width: 100%%;
                        max-width: 560px;
                        margin: 0 auto;

                        background-color: #ffffff;

                        border: 1px solid #e4e4e7;
                        border-radius: 12px;

                        overflow: hidden;
                    }

                    /* ================================
                       Header / Logo
                       ================================ */

                    .header {
                        padding: 30px 32px 26px;

                        text-align: center;

                        background-color: #ffffff;

                        border-bottom: 1px solid #f4f4f5;
                    }

                    .header-logo {
                        display: block;

                        width: 150px;
                        max-width: 150px;
                        height: auto;

                        margin: 0 auto;

                        border: 0;
                        outline: none;
                        text-decoration: none;
                    }

                    /* ================================
                       Main Content
                       ================================ */

                    .content {
                        padding: 38px 36px 36px;

                        text-align: center;

                        background-color: #ffffff;
                    }

                    /* ================================
                       Small Section Label
                       ================================ */

                    .eyebrow {
                        margin: 0 0 12px;

                        font-size: 10px;
                        line-height: 1.4;

                        font-weight: 700;

                        letter-spacing: 0.14em;

                        text-transform: uppercase;

                        color: #71717a;
                    }

                    /* ================================
                       Main Heading
                       ================================ */

                    .title {
                        margin: 0 0 12px;

                        font-size: 24px;
                        line-height: 1.25;

                        font-weight: 800;

                        letter-spacing: -0.035em;

                        color: #000000;
                    }

                    /* ================================
                       Description
                       ================================ */

                    .description {
                        max-width: 450px;

                        margin: 0 auto 28px;

                        font-size: 14px;
                        line-height: 1.7;

                        font-weight: 400;

                        color: #52525b;
                    }

                    .recipient {
                        font-weight: 700;
                        color: #18181b;
                    }

                    /* ================================
                       CTA Button
                       ================================ */

                    .button-wrapper {
                        padding: 2px 0 0;
                    }

                    .button {
                        display: inline-block;

                        padding: 13px 24px;

                        background-color: #000000;

                        color: #ffffff !important;

                        border: 1px solid #000000;
                        border-radius: 7px;

                        font-size: 12px;
                        line-height: 20px;

                        font-weight: 700;

                        letter-spacing: 0.07em;

                        text-transform: uppercase;

                        text-decoration: none !important;
                    }

                    /* ================================
                       Secure URL Box
                       ================================ */

                    .url-box {
                        margin-top: 28px;

                        padding: 14px 16px;

                        background-color: #fafafa;

                        border: 1px solid #f0f0f2;

                        border-radius: 7px;

                        text-align: left;
                    }

                    .url-label {
                        display: block;

                        margin-bottom: 6px;

                        font-size: 10px;
                        line-height: 1.4;

                        font-weight: 700;

                        letter-spacing: 0.1em;

                        text-transform: uppercase;

                        color: #71717a;
                    }

                    .url {
                        display: block;

                        font-size: 12px;
                        line-height: 1.6;

                        color: #18181b;

                        word-break: break-all;
                        overflow-wrap: anywhere;
                    }

                    /* ================================
                       Footer
                       ================================ */

                    .footer {
                        padding: 22px 32px 24px;

                        background-color: #fafafa;

                        border-top: 1px solid #f4f4f5;

                        text-align: center;
                    }

                    .footer-text {
                        margin: 0;

                        font-size: 11px;
                        line-height: 1.7;

                        font-weight: 400;

                        color: #71717a;
                    }

                    .footer-email {
                        font-weight: 600;

                        color: #52525b;
                    }

                    /* ================================
                       Tracking Pixel
                       ================================ */

                    .tracking-pixel {
                        display: none !important;

                        width: 1px !important;
                        height: 1px !important;

                        border: 0 !important;
                        outline: none !important;

                        opacity: 0 !important;
                    }

                    /* ================================
                       Mobile
                       ================================ */

                    @media only screen and (max-width: 600px) {

                        .page {
                            padding: 24px 12px;
                        }

                        .container {
                            width: 100%% !important;
                            border-radius: 10px;
                        }

                        .header {
                            padding: 26px 20px 22px;
                        }

                        .header-logo {
                            width: 135px;
                            max-width: 135px;
                        }

                        .content {
                            padding: 32px 22px;
                        }

                        .title {
                            font-size: 22px;
                            line-height: 1.3;
                        }

                        .description {
                            font-size: 13.5px;
                            line-height: 1.7;
                        }

                        .button {
                            padding: 12px 22px;
                            font-size: 11px;
                        }

                        .url-box {
                            padding: 13px 14px;
                        }

                        .url {
                            font-size: 11.5px;
                        }

                        .footer {
                            padding: 20px;
                        }

                        .footer-text {
                            font-size: 10.5px;
                        }
                    }

                    /* ================================
                       Very Small Screens
                       ================================ */

                    @media only screen and (max-width: 380px) {

                        .page {
                            padding: 16px 8px;
                        }

                        .content {
                            padding: 28px 18px;
                        }

                        .title {
                            font-size: 21px;
                        }

                        .description {
                            font-size: 13px;
                        }

                        .header-logo {
                            width: 125px;
                            max-width: 125px;
                        }
                    }
                </style>
            </head>

            <body>

                <div class="page">

                    <div class="container">

                        <!-- ================================
                             Brand / Logo
                             ================================ -->

                        <div class="header">

                            <img
                                    src="https://adpndt.sirv.com/Images/logo_black_3.png"
                                    alt="SecureURLs.me"
                                    class="header-logo"
                                    width="150"
                            />

                        </div>

                        <!-- ================================
                             Main Content
                             ================================ -->

                        <div class="content">

                            <p class="eyebrow">
                                Secure Access
                            </p>

                            <h1 class="title">
                                Secure Link Access Granted
                            </h1>

                            <p class="description">
                                You have been granted confidential access to a secured
                                link. To open the destination, click the button below.
                                You will be prompted to authenticate with an email OTP
                                code sent to
                                <span class="recipient">%s</span>.
                            </p>

                            <!-- ================================
                                 CTA
                                 ================================ -->

                            <div class="button-wrapper">

                                <a
                                        href="%s"
                                        class="button"
                                        target="_blank"
                                        rel="noopener"
                                >
                                    Open Secure Link &rarr;
                                </a>

                            </div>

                            <!-- ================================
                                 Direct URL
                                 ================================ -->

                            <div class="url-box">

                                <span class="url-label">
                                    Secure Link
                                </span>

                                <a
                                        href="%s"
                                        class="url"
                                        target="_blank"
                                        rel="noopener"
                                >
                                    %s
                                </a>

                            </div>

                        </div>

                        <!-- ================================
                             Footer
                             ================================ -->

                        <div class="footer">

                            <p class="footer-text">

                                Protected by SecureURLs.me
                                &bull;
                                Secure Access Verification

                                <br>

                                This link is intended strictly for
                                <span class="footer-email">%s</span>.

                            </p>

                        </div>

                    </div>

                </div>

                <!-- ================================
                     1px Open Tracking Pixel
                     ================================ -->

                <img
                        src="%s"
                        width="1"
                        height="1"
                        alt=""
                        class="tracking-pixel"
                        style="
                            display:none !important;
                            width:1px !important;
                            height:1px !important;
                            border:0 !important;
                            outline:none !important;
                            opacity:0 !important;
                        "
                />

            </body>

            </html>
            """.formatted(
                toEmail,
                linkUrl,
                linkUrl,
                linkUrl,
                toEmail,
                trackingPixelUrl
        );
    }

    @Override
    public void sendLinkAccessOtp(String toEmail, String otpCode, String linkUrl) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("PREONS-EMAILER: Cannot send link OTP because recipient email is blank.");
            return;
        }

        String formattedCode = (otpCode != null && otpCode.length() == 6)
                ? String.join(" ", otpCode.split(""))
                : (otpCode != null ? otpCode : "");

        String subject = "Your Link Access Code: " + otpCode;
        String htmlContent = buildLinkAccessOtpHtml(toEmail, formattedCode, linkUrl);
        String textContent = "Your PreonsURL verification code to access " + linkUrl + " is:\n\n" +
                otpCode + "\n\nThis code expires in 10 minutes.";

        log.info("PREONS-EMAILER [Mailtrap API] Dispatching link OTP '{}' to '{}' (link: '{}')", otpCode, toEmail, linkUrl);

        if (apiToken == null || apiToken.isBlank()) {
            log.info("PREONS-EMAILER: Mailtrap API token not configured. Link OTP '{}' logged for '{}'.", otpCode, toEmail);
            return;
        }

        try {
            MailtrapClient client = getClient();
            if (client == null) {
                log.info("PREONS-EMAILER: MailtrapClient unavailable. Link OTP '{}' logged for '{}'.", otpCode, toEmail);
                return;
            }

            Address from = new Address(senderEmail, senderName);
            Address to = new Address(toEmail, toEmail);

            MailtrapMail mail = MailtrapMail.builder()
                    .from(from)
                    .to(List.of(to))
                    .subject(subject)
                    .html(htmlContent)
                    .text(textContent)
                    .category("Link Access OTP")
                    .build();

            SendResponse response = client.send(mail);
            log.info("PREONS-EMAILER: Link OTP email successfully sent to '{}' (response: {})", toEmail, response);
        } catch (Exception e) {
            log.warn("PREONS-EMAILER: Failed to dispatch link OTP to '{}': {}", toEmail, e.getMessage());
        }
    }

    private String buildLinkAccessOtpHtml(String toEmail, String formattedCode, String linkUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                
                <head>
                	<meta charset="UTF-8">
                	<meta name="viewport" content="width=device-width, initial-scale=1.0">
                	<title>Your One-Time Passcode</title>
                	<style>
                	/* ================================ Base / Email Reset ================================ */
                
                	html,
                	body {
                		margin: 0 !important;
                		padding: 0 !important;
                		width: 100%% !important;
                		min-width: 100%% !important;
                		background-color: #f7f7f7;
                	}
                
                	body {
                		font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                		color: #000000;
                		-webkit-font-smoothing: antialiased;
                		-moz-osx-font-smoothing: grayscale;
                		text-rendering: optimizeLegibility;
                	}
                
                	body,
                	table,
                	td,
                	p,
                	a {
                		-webkit-text-size-adjust: 100%%;
                		-ms-text-size-adjust: 100%%;
                	}
                
                	table,
                	td {
                		mso-table-lspace: 0pt;
                		mso-table-rspace: 0pt;
                	}
                
                	img {
                		-ms-interpolation-mode: bicubic;
                		border: 0;
                		outline: none;
                		text-decoration: none;
                		display: block;
                		height: auto;
                		line-height: 100%%;
                	}
                
                	table {
                		border-collapse: collapse !important;
                		border-spacing: 0;
                	}
                
                	a {
                		color: inherit;
                		text-decoration: none;
                	}
                	/* ================================ Page ================================ */
                
                	.page {
                		width: 100%%;
                		padding: 48px 20px;
                		box-sizing: border-box;
                		background-color: #f7f7f7;
                	}
                	/* ================================ Email Container ================================ */
                
                	.container {
                		width: 100%%;
                		max-width: 560px;
                		margin: 0 auto;
                		background-color: #ffffff;
                		border: 1px solid #d4d4d4;
                		border-radius: 12px;
                		overflow: hidden;
                	}
                	/* ================================ Header / Brand ================================ */
                
                	.header {
                		padding: 30px 32px 26px;
                		text-align: center;
                		background-color: #ffffff;
                		border-bottom: 1px solid #e5e5e5;
                	}
                
                	.header-logo {
                		display: block;
                		width: 150px;
                		max-width: 150px;
                		height: auto;
                		margin: 0 auto;
                		border: 0;
                		outline: none;
                		text-decoration: none;
                	}
                	/* ================================ Content ================================ */
                
                	.content {
                		padding: 38px 36px 36px;
                		text-align: center;
                		background-color: #ffffff;
                	}
                	/* ================================ Eyebrow ================================ */
                
                	.eyebrow {
                		margin: 0 0 12px;
                		font-size: 10px;
                		line-height: 1.4;
                		font-weight: 700;
                		letter-spacing: 0.14em;
                		text-transform: uppercase;
                		color: #737373;
                	}
                	/* ================================ Title ================================ */
                
                	.title {
                		margin: 0 0 12px;
                		font-size: 24px;
                		line-height: 1.25;
                		font-weight: 800;
                		letter-spacing: -0.035em;
                		color: #000000;
                	}
                	/* ================================ Description ================================ */
                
                	.description {
                		max-width: 440px;
                		margin: 0 auto 28px;
                		font-size: 14px;
                		line-height: 1.7;
                		font-weight: 400;
                		color: #525252;
                	}
                
                	.recipient {
                		font-weight: 700;
                		color: #181818;
                	}
                	/* ================================ OTP Code ================================ */
                
                	.code-label {
                		margin: 0 0 10px;
                		font-size: 10px;
                		line-height: 1.4;
                		font-weight: 700;
                		letter-spacing: 0.12em;
                		text-transform: uppercase;
                		color: #737373;
                	}
                
                	.code-display {
                		display: inline-block;
                		padding: 16px 26px;
                		background-color: #f3f3f3;
                		border: 1px solid #d4d4d4;
                		border-radius: 8px;
                		color: #000000;
                		font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
                		font-size: 30px;
                		line-height: 1.2;
                		font-weight: 800;
                		letter-spacing: 0.22em;
                		white-space: nowrap;
                	}
                	/* ================================ Expiry Note ================================ */
                
                	.expiry-note {
                		max-width: 430px;
                		margin: 18px auto 0;
                		font-size: 12px;
                		line-height: 1.7;
                		color: #737373;
                	}
                
                	.expiry-note strong {
                		color: #262626;
                		font-weight: 700;
                	}
                	/* ================================ Security Notice ================================ */
                
                	.security-note {
                		margin: 26px 0 0;
                		padding: 12px 14px;
                		background-color: #fafafa;
                		border: 1px solid #e5e5e5;
                		border-radius: 7px;
                		font-size: 11px;
                		line-height: 1.6;
                		color: #737373;
                		text-align: left;
                	}
                	/* ================================ Footer ================================ */
                
                	.footer {
                		padding: 22px 32px 24px;
                		background-color: #fafafa;
                		border-top: 1px solid #e5e5e5;
                		text-align: center;
                	}
                
                	.footer-text {
                		margin: 0;
                		font-size: 11px;
                		line-height: 1.7;
                		font-weight: 400;
                		color: #737373;
                	}
                
                	.footer-brand {
                		color: #404040;
                		font-weight: 600;
                	}
                	/* ================================ Mobile ================================ */
                
                	@media only screen and (max-width: 600px) {
                		.page {
                			padding: 24px 12px;
                		}
                		.container {
                			width: 100%% !important;
                			border-radius: 10px;
                		}
                		.header {
                			padding: 26px 20px 22px;
                		}
                		.header-logo {
                			width: 135px;
                			max-width: 135px;
                		}
                		.content {
                			padding: 32px 22px;
                		}
                		.title {
                			font-size: 22px;
                			line-height: 1.3;
                		}
                		.description {
                			font-size: 13.5px;
                			line-height: 1.7;
                		}
                		.code-display {
                			padding: 15px 20px;
                			font-size: 27px;
                			letter-spacing: 0.18em;
                		}
                		.expiry-note {
                			font-size: 11.5px;
                		}
                		.security-note {
                			font-size: 10.5px;
                		}
                		.footer {
                			padding: 20px;
                		}
                		.footer-text {
                			font-size: 10.5px;
                		}
                	}
                	/* ================================ Very Small Screens ================================ */
                
                	@media only screen and (max-width: 380px) {
                		.page {
                			padding: 16px 8px;
                		}
                		.content {
                			padding: 28px 18px;
                		}
                		.title {
                			font-size: 21px;
                		}
                		.description {
                			font-size: 13px;
                		}
                		.header-logo {
                			width: 125px;
                			max-width: 125px;
                		}
                		.code-display {
                			padding: 14px 16px;
                			font-size: 24px;
                			letter-spacing: 0.15em;
                		}
                	}
                	</style>
                </head>
                
                <body>
                	<div class="page">
                		<div class="container">
                			<!-- ================================ Brand / Logo ================================ -->
                			<div class="header"> <img src="https://adpndt.sirv.com/Images/logo_black_3.png" alt="SecureURLs.me" class="header-logo" width="150" /> </div>
                			<!-- ================================ Main Content ================================ -->
                			<div class="content">
                				<p class="eyebrow"> Secure Access </p>
                				<h1 class="title"> Your Link Verification Code </h1>
                				<p class="description"> Enter this 6-digit code on the verification screen to access the protected destination. </p>
                				<p class="code-label"> One-Time Passcode </p>
                				<div class="code-display">%s</div>
                				<p class="expiry-note"> This one-time passcode is valid for <strong>10 minutes</strong> and can only be used once. </p>
                				<div class="security-note"> For your security, never share this verification code with anyone. SecureURLs will never ask you to provide this code outside the verification process. </div>
                			</div>
                			<!-- ================================ Footer ================================ -->
                			<div class="footer">
                				<p class="footer-text"> If you did not request this verification code, please ignore this email.
                					<br> <span class="footer-brand"> Secure Access Verification </span> </p>
                			</div>
                		</div>
                	</div>
                </body>
                
                </html>
                """.formatted(formattedCode);
    }
}
