package com.preonsurl.apis.link.dto;

import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.UsagePolicy;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Encapsulates the outcome of evaluating usage and access policies for a URL during serving.
 */
public record PolicyEvaluationResult(
        Status status,
        ViolationType violationType,
        HttpStatus httpStatus,
        String title,
        String description,
        String badge,
        String infoMessage,
        String iconType,
        Map<String, String> details,
        AccessPolicy accessPolicy,
        UsagePolicy usagePolicy
) {
    public enum Status {
        ALLOWED,
        CHALLENGE_REQUIRED,
        REJECTED,
        NOT_FOUND
    }

    public enum ViolationType {
        NOT_FOUND,
        INACTIVE,
        EXPIRED,
        USAGE_LIMIT_EXCEEDED,
        OUTSIDE_SCHEDULE,
        IP_RESTRICTED,
        COUNTRY_RESTRICTED,
        DEVICE_RESTRICTED,
        REFERRER_RESTRICTED,
        INVALID_CREDENTIALS
    }

    public boolean isAllowed() {
        return status == Status.ALLOWED;
    }

    public boolean isChallengeRequired() {
        return status == Status.CHALLENGE_REQUIRED;
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }

    public boolean isNotFound() {
        return status == Status.NOT_FOUND;
    }

    public static PolicyEvaluationResult allowed(AccessPolicy accessPolicy, UsagePolicy usagePolicy) {
        return new PolicyEvaluationResult(
                Status.ALLOWED,
                null,
                HttpStatus.OK,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                accessPolicy,
                usagePolicy
        );
    }

    public static PolicyEvaluationResult challengeRequired(AccessPolicy accessPolicy, UsagePolicy usagePolicy) {
        return new PolicyEvaluationResult(
                Status.CHALLENGE_REQUIRED,
                null,
                HttpStatus.OK,
                "Security Challenge Required",
                "This link is protected. Please provide the required PIN or password.",
                "Protected",
                "Authentication required before accessing this protected resource.",
                "security",
                Map.of(),
                accessPolicy,
                usagePolicy
        );
    }

    public static PolicyEvaluationResult notFound(String message) {
        return new PolicyEvaluationResult(
                Status.NOT_FOUND,
                ViolationType.NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Link Not Found",
                message != null ? message : "The requested short link does not exist or has been removed.",
                "Not Found",
                "Please verify the URL you entered or contact the person who provided this link.",
                "alert",
                Map.of(),
                null,
                null
        );
    }

    public static PolicyEvaluationResult rejected(
            ViolationType violationType,
            HttpStatus httpStatus,
            String title,
            String description,
            String badge,
            String infoMessage,
            String iconType,
            Map<String, String> details,
            AccessPolicy accessPolicy,
            UsagePolicy usagePolicy
    ) {
        return new PolicyEvaluationResult(
                Status.REJECTED,
                violationType,
                httpStatus,
                title,
                description,
                badge,
                infoMessage,
                iconType != null ? iconType : "alert",
                details != null ? details : Map.of(),
                accessPolicy,
                usagePolicy
        );
    }
}
