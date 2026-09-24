package com.preonsurl.apis.link.policy.evaluator;

import com.preonsurl.apis.link.entity.AccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Validates submitted credentials (PIN and/or Password) against an AccessPolicy.
 * Securely hashes and compares credentials without logging sensitive data.
 */
@Component
public class CredentialVerifier {

    private static final Logger log = LoggerFactory.getLogger(CredentialVerifier.class);

    private final PasswordEncoder passwordEncoder;

    public CredentialVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public record Outcome(boolean valid, String errorMessage) {
        public static Outcome success() {
            return new Outcome(true, null);
        }

        public static Outcome failure(String errorMessage) {
            return new Outcome(false, errorMessage);
        }
    }

    /**
     * Verifies submitted PIN and/or Password credentials against the AccessPolicy.
     *
     * @param policy The AccessPolicy entity containing BCrypt credential hashes
     * @param submittedPin Raw PIN entered by the user (nullable)
     * @param submittedPassword Raw password entered by the user (nullable)
     * @return Outcome indicating success or failure with appropriate user error message
     */
    public Outcome verify(AccessPolicy policy, String submittedPin, String submittedPassword) {
        if (policy == null || !policy.isPinOrPasswordProtected()) {
            return Outcome.success();
        }

        boolean pinValid = true;
        if (policy.hasPin()) {
            pinValid = submittedPin != null && !submittedPin.isBlank()
                    && passwordEncoder.matches(submittedPin.trim(), policy.getPinHash());
        }

        boolean passwordValid = true;
        if (policy.hasPassword()) {
            passwordValid = submittedPassword != null && !submittedPassword.isBlank()
                    && passwordEncoder.matches(submittedPassword, policy.getPasswordHash());
        }

        if (pinValid && passwordValid) {
            log.info("Credentials verified successfully for shortUrlId={}", policy.getShortUrlId());
            return Outcome.success();
        }

        log.warn("Credential verification failed for shortUrlId={} (pinValid={}, passwordValid={})",
                policy.getShortUrlId(), pinValid, passwordValid);

        String errorMsg;
        if (policy.hasPin() && policy.hasPassword()) {
            errorMsg = "Invalid PIN or Password. Please check your credentials and try again.";
        } else if (policy.hasPin()) {
            errorMsg = "Invalid PIN. Please try again.";
        } else {
            errorMsg = "Invalid Password. Please try again.";
        }

        return Outcome.failure(errorMsg);
    }
}
