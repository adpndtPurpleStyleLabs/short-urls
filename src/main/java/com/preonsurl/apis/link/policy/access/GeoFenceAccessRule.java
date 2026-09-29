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
        String geofenceJson = context.accessPolicy().getGeofence();
        Double clientLat = context.clientCoordinates() != null ? context.clientCoordinates().latitude() : null;
        Double clientLng = context.clientCoordinates() != null ? context.clientCoordinates().longitude() : null;

        boolean allowed = geoFencePolicyEvaluator.isAllowed(clientLat, clientLng, geofenceJson, context.clientIp());

        if (!allowed) {
            GeoFencePolicy policy = geoFencePolicyEvaluator.parsePolicy(geofenceJson);
            String action = policy != null ? policy.action() : "ALLOW";
            String fenceName = policy != null ? policy.name() : null;

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
