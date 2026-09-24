package com.preonsurl.apis.link.policy.access;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.policy.LinkPolicyRule;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.evaluator.DevicePolicyEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces authorized device type and platform access restrictions.
 */
@Component
@Order(DeviceAccessRule.ORDER)
public class DeviceAccessRule implements LinkPolicyRule {

    public static final int ORDER = 120;
    private static final Logger log = LoggerFactory.getLogger(DeviceAccessRule.class);

    private final DevicePolicyEvaluator devicePolicyEvaluator;
    private final PolicyViolationResultFactory resultFactory;

    public DeviceAccessRule(DevicePolicyEvaluator devicePolicyEvaluator, PolicyViolationResultFactory resultFactory) {
        this.devicePolicyEvaluator = devicePolicyEvaluator;
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        return context != null
                && context.isSecured()
                && context.accessPolicy().getDeviceTypes() != null
                && !context.accessPolicy().getDeviceTypes().isBlank();
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        String allowedDevices = context.accessPolicy().getDeviceTypes();
        if (!devicePolicyEvaluator.isAllowed(context.deviceInfo(), allowedDevices)) {
            log.warn("Access rejected by Device policy: id={}, url='{}', detectedDevice='{}', allowed='{}'",
                    context.shortUrlId(), context.newUrl(),
                    context.deviceInfo() != null ? context.deviceInfo().humanReadable() : "Unknown",
                    allowedDevices);

            return resultFactory.deviceRestricted(
                    context.shortUrlId(),
                    context.newUrl(),
                    context.deviceInfo(),
                    allowedDevices,
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
