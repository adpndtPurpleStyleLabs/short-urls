package com.preonsurl.apis.link.policy.access;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.policy.LinkPolicyRule;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.evaluator.CredentialVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces PIN and Password security challenges or validates submitted credentials.
 */
@Component
@Order(CredentialAccessRule.ORDER)
public class CredentialAccessRule implements LinkPolicyRule {

    public static final int ORDER = 200;
    private static final Logger log = LoggerFactory.getLogger(CredentialAccessRule.class);

    private final CredentialVerifier credentialVerifier;
    private final PolicyViolationResultFactory resultFactory;

    public CredentialAccessRule(CredentialVerifier credentialVerifier, PolicyViolationResultFactory resultFactory) {
        this.credentialVerifier = credentialVerifier;
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        return context != null && context.isPinOrPasswordProtected();
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        // 1. If this is a credential submission verification flow (POST)
        if (context.isVerificationFlow()) {
            CredentialVerifier.Outcome outcome = credentialVerifier.verify(
                    context.accessPolicy(),
                    context.submittedPin(),
                    context.submittedPassword()
            );

            if (!outcome.valid()) {
                log.warn("Security challenge authentication rejected for shortUrlId={}, url='{}'",
                        context.shortUrlId(), context.newUrl());

                return resultFactory.invalidCredentials(
                        context.shortUrlId(),
                        context.newUrl(),
                        context.clientIp(),
                        outcome.errorMessage(),
                        context.accessPolicy(),
                        context.usagePolicy()
                );
            }

            // Credentials successfully matched
            return null;
        }

        // 2. Normal visit flow: check if already authenticated by security cookie
        if (context.verifiedByCookie()) {
            return null;
        }

        // Prompt security challenge
        log.info("Prompting PIN/Password challenge for shortUrlId={}, url='{}'",
                context.shortUrlId(), context.newUrl());

        return resultFactory.challengeRequired(context.accessPolicy(), context.usagePolicy());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
