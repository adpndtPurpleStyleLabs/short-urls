package com.preonsurl.apis.link.ui;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class LinkUiRenderer {

    private final ITemplateEngine templateEngine;

    public LinkUiRenderer(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Renders the PIN/Password Security Challenge page using Thymeleaf.
     */
    public String renderSecurityChallenge(
            String targetPath,
            boolean requiresPin,
            boolean requiresPassword,
            String errorMessage
    ) {
        Context context = new Context();
        context.setVariable("targetPath", targetPath);
        context.setVariable("errorMessage", errorMessage);
        context.setVariable("requiresPin", requiresPin);
        context.setVariable("requiresPassword", requiresPassword);

        if (requiresPin && !requiresPassword) {
            return templateEngine.process("challenge-pin", context);
        } else if (requiresPassword && !requiresPin) {
            return templateEngine.process("challenge-password", context);
        } else {
            return templateEngine.process("challenge-combined", context);
        }
    }

    /**
     * Renders the Exhausted / Inaccessible Link webpage using Thymeleaf.
     */
    public String renderExhaustedPage(
            String reasonTitle,
            String reasonDescription,
            String badgeText
    ) {
        Context context = new Context();
        context.setVariable("title", reasonTitle);
        context.setVariable("description", reasonDescription);
        context.setVariable("badge", badgeText);
        return templateEngine.process("link-exhausted", context);
    }
}
