package com.preonsurl.apis.link.policy.access;

import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.GeoFencePolicy;
import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.policy.LinkPolicyRule;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.evaluator.GeoFencePolicyEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces geographic perimeter boundary rules (GeoFence) using circular radius or polygon vertex evaluation.
 */
@Component
@Order(GeoFenceAccessRule.ORDER)
public class GeoFenceAccessRule implements LinkPolicyRule {

    public static final int ORDER = 115; // Between CountryAccessRule (110) and DeviceAccessRule (120)
    private static final Logger log = LoggerFactory.getLogger(GeoFenceAccessRule.class);

    private final GeoFencePolicyEvaluator geoFencePolicyEvaluator;
    private final PolicyViolationResultFactory resultFactory;

    public GeoFenceAccessRule(GeoFencePolicyEvaluator geoFencePolicyEvaluator, PolicyViolationResultFactory resultFactory) {
        this.geoFencePolicyEvaluator = geoFencePolicyEvaluator;
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        return context != null
                && context.isSecured()
                && context.accessPolicy() != null
                && context.accessPolicy().hasGeofence();
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        // If already authorized by session cookie or geofence verification cookie, allow without re-prompt
        if (context.geofenceVerifiedByCookie() || context.verifiedByCookie()) {
            return null;
        }

        String geofenceJson = context.accessPolicy().getGeofence();
        GeoFencePolicy policy = geoFencePolicyEvaluator.parsePolicy(geofenceJson);
        String action = policy != null ? policy.action() : "ALLOW";
        String fenceName = policy != null ? policy.name() : null;

        Double clientLat = context.clientCoordinates() != null ? context.clientCoordinates().latitude() : null;
        Double clientLng = context.clientCoordinates() != null ? context.clientCoordinates().longitude() : null;

        // If coordinates have not been provided yet (typical initial direct link visit in browser)
        if (clientLat == null || clientLng == null) {
            boolean loopbackAllowed = geoFencePolicyEvaluator.isAllowed(null, null, geofenceJson, context.clientIp());
            if (loopbackAllowed) {
                return null;
            }

            log.info("Client coordinates absent for GeoFence protected link: id={}, url='{}', rule='{}'. Triggering browser location challenge.",
                    context.shortUrlId(), context.newUrl(), action);

            return resultFactory.geofenceChallengeRequired(
                    context.accessPolicy(),
                    context.usagePolicy(),
                    fenceName,
                    action
            );
        }

        // Coordinates present: evaluate physical boundary
        boolean allowed = geoFencePolicyEvaluator.isAllowed(clientLat, clientLng, geofenceJson, context.clientIp());

        if (!allowed) {
            log.warn("Access rejected by GeoFence boundary policy: id={}, url='{}', clientCoords=({}, {}), rule='{}'",
                    context.shortUrlId(), context.newUrl(), clientLat, clientLng, action);

            return resultFactory.geofenceRestricted(
                    context.shortUrlId(),
                    context.newUrl(),
                    clientLat,
                    clientLng,
                    fenceName,
                    action,
                    context.accessPolicy(),
                    context.usagePolicy()
            );
        }

        return null;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
