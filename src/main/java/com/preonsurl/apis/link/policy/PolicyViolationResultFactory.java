package com.preonsurl.apis.link.policy;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.dto.PolicyEvaluationResult.ViolationType;
import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.UsagePolicy;
import com.preonsurl.apis.link.policy.model.DeviceInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for creating standardized, informative PolicyEvaluationResult instances
 * with appropriate HTTP status codes, user-facing titles, descriptions, icons, badges,
 * and diagnostic details.
 */
@Component
public class PolicyViolationResultFactory {

    public PolicyEvaluationResult inactive(Long id, String url) {
        return PolicyEvaluationResult.rejected(
                ViolationType.INACTIVE,
                HttpStatus.NOT_FOUND,
                "Link Inactive",
                "This short link is currently disabled or inactive.",
                "Inactive",
                "The creator of this link has disabled access.",
                "alert",
                Map.of("Status", "Inactive"),
                null,
                null
        );
    }

    public PolicyEvaluationResult expired(Long id, String url, Instant expireAt, UsagePolicy usagePolicy) {
        Instant effectiveExpire = expireAt != null ? expireAt : (usagePolicy != null ? usagePolicy.getExpireAt() : Instant.now());
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Status", "Expired");
        if (effectiveExpire != null) {
            details.put("Expiration Date", effectiveExpire.toString());
        }

        return PolicyEvaluationResult.rejected(
                ViolationType.EXPIRED,
                HttpStatus.GONE,
                "Link Has Expired",
                "This short link expired on " + effectiveExpire + " and is no longer accessible.",
                "Expired",
                "This URL has reached the end of its active lifecycle. If you believe this is an error, please contact the individual or organization who shared this link with you.",
                "clock",
                details,
                null,
                usagePolicy
        );
    }

    public PolicyEvaluationResult usageLimitExceeded(Long id, String url, long clickCount, long maxLimit, UsagePolicy usagePolicy) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Status", "Limit Reached");
        details.put("Max Allowed Accesses", String.valueOf(maxLimit));
        details.put("Total Uses", String.valueOf(clickCount));

        return PolicyEvaluationResult.rejected(
                ViolationType.USAGE_LIMIT_EXCEEDED,
                HttpStatus.GONE,
                "Usage Limit Reached",
                "This short link has reached its maximum allowed number of accesses and is no longer accessible.",
                "Limit Reached",
                "This URL has reached the end of its active lifecycle. If you believe this is an error, please contact the individual or organization who shared this link with you.",
                "limit",
                details,
                null,
                usagePolicy
        );
    }

    public PolicyEvaluationResult outsideSchedule(Long id, String url, Instant startAt, Instant endAt, UsagePolicy usagePolicy) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Status", "Outside Schedule Window");
        if (startAt != null) details.put("Access Window Start", startAt.toString());
        if (endAt != null) details.put("Access Window End", endAt.toString());

        return PolicyEvaluationResult.rejected(
                ViolationType.OUTSIDE_SCHEDULE,
                HttpStatus.FORBIDDEN,
                "Link Outside Schedule",
                "This short link is not currently accessible according to its access schedule.",
                "Schedule Inactive",
                "Access to this link is only permitted during specified operational windows. Please revisit during the scheduled period.",
                "clock",
                details,
                null,
                usagePolicy
        );
    }

    public PolicyEvaluationResult ipRestricted(Long id, String url, String clientIp, String allowlist,
                                              AccessPolicy accessPolicy, UsagePolicy usagePolicy) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Your IP Address", clientIp != null ? clientIp : "Unknown");
        details.put("Access Rule", "Restricted to authorized IP allowlist");

        return PolicyEvaluationResult.rejected(
                ViolationType.IP_RESTRICTED,
                HttpStatus.FORBIDDEN,
                "Access Restricted: IP Not Allowed",
                "Your IP address (" + clientIp + ") is not authorized to access this link. This link is restricted to specific IP addresses or network ranges.",
                "IP Restricted",
                "The creator has restricted access to an approved IP allowlist. If you need access from this network, contact the link owner to add your IP address.",
                "security",
                details,
                accessPolicy,
                usagePolicy
        );
    }

    public PolicyEvaluationResult countryRestricted(Long id, String url, String clientCountry, String allowedCountries,
                                                   AccessPolicy accessPolicy, UsagePolicy usagePolicy) {
        String detected = clientCountry != null ? clientCountry : "Unknown";
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Allowed Countries", allowedCountries != null ? allowedCountries : "None");
        details.put("Detected Country", detected);

        return PolicyEvaluationResult.rejected(
                ViolationType.COUNTRY_RESTRICTED,
                HttpStatus.FORBIDDEN,
                "Access Restricted: Country Restriction",
                "This link is restricted to visitors from specific geographic regions (" + allowedCountries + "). Your detected region is " + detected + ".",
                "Country Blocked",
                "Geographic access restrictions are enforced by the link creator. Access is not permitted from your current country or territory.",
                "security",
                details,
                accessPolicy,
                usagePolicy
        );
    }

    public PolicyEvaluationResult deviceRestricted(Long id, String url, DeviceInfo device, String allowedDevices,
                                                  AccessPolicy accessPolicy, UsagePolicy usagePolicy) {
        String detected = device != null ? device.humanReadable() : "Unknown Device";
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Allowed Devices", allowedDevices != null ? allowedDevices : "None");
        details.put("Detected Device", detected);

        return PolicyEvaluationResult.rejected(
                ViolationType.DEVICE_RESTRICTED,
                HttpStatus.FORBIDDEN,
                "Access Restricted: Device Incompatible",
                "This link can only be accessed from authorized device types (" + allowedDevices + "). Your current device was detected as " + detected + ".",
                "Device Incompatible",
                "The creator has restricted access to specific device types (such as Mobile or Desktop). Please open this link on an authorized device.",
                "security",
                details,
                accessPolicy,
                usagePolicy
        );
    }

    public PolicyEvaluationResult referrerRestricted(Long id, String url, String referer, String allowedReferrers,
                                                    AccessPolicy accessPolicy, UsagePolicy usagePolicy) {
        String displayRef = (referer != null && !referer.isBlank()) ? referer : "Direct Access (No Referer)";
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Detected Referrer", displayRef);
        details.put("Allowed Sources", allowedReferrers != null ? allowedReferrers : "None");

        return PolicyEvaluationResult.rejected(
                ViolationType.REFERRER_RESTRICTED,
                HttpStatus.FORBIDDEN,
                "Access Restricted: Unauthorized Referrer",
                "Access to this link is only permitted when referred by authorized sources (" + allowedReferrers + "). Your referrer was: " + displayRef + ".",
                "Referrer Blocked",
                "Direct visits or unauthorized referral traffic are blocked for this link. You must follow the link from an approved website or partner portal.",
                "security",
                details,
                accessPolicy,
                usagePolicy
        );
    }

    public PolicyEvaluationResult invalidCredentials(Long id, String url, String clientIp, String errorMessage,
                                                    AccessPolicy accessPolicy, UsagePolicy usagePolicy) {
        return PolicyEvaluationResult.rejected(
                ViolationType.INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED,
                "Invalid Credentials",
                errorMessage != null ? errorMessage : "Invalid PIN or Password.",
                "Unauthorized",
                "The credentials entered do not match our records. Please try again.",
                "security",
                Map.of("Status", "Authentication Failed"),
                accessPolicy,
                usagePolicy
        );
    }

    public PolicyEvaluationResult challengeRequired(AccessPolicy accessPolicy, UsagePolicy usagePolicy) {
        return PolicyEvaluationResult.challengeRequired(accessPolicy, usagePolicy);
    }

    public PolicyEvaluationResult allowed(AccessPolicy accessPolicy, UsagePolicy usagePolicy) {
        return PolicyEvaluationResult.allowed(accessPolicy, usagePolicy);
    }
}
