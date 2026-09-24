package com.preonsurl.apis.link.policy.access;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.policy.LinkPolicyRule;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.evaluator.IpPolicyEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces authorized IP address and CIDR range access restrictions.
 */
@Component
@Order(IpAccessRule.ORDER)
public class IpAccessRule implements LinkPolicyRule {

    public static final int ORDER = 100;
    private static final Logger log = LoggerFactory.getLogger(IpAccessRule.class);

    private final IpPolicyEvaluator ipPolicyEvaluator;
    private final PolicyViolationResultFactory resultFactory;

    public IpAccessRule(IpPolicyEvaluator ipPolicyEvaluator, PolicyViolationResultFactory resultFactory) {
        this.ipPolicyEvaluator = ipPolicyEvaluator;
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        return context != null
                && context.isSecured()
                && context.accessPolicy().getIpAllowlist() != null
                && !context.accessPolicy().getIpAllowlist().isBlank();
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        String allowlist = context.accessPolicy().getIpAllowlist();
        if (!ipPolicyEvaluator.isAllowed(context.clientIp(), allowlist)) {
            log.warn("Access rejected by IP allowlist: id={}, url='{}', clientIp='{}', allowlist='{}'",
                    context.shortUrlId(), context.newUrl(), context.clientIp(), allowlist);

            return resultFactory.ipRestricted(
                    context.shortUrlId(),
                    context.newUrl(),
                    context.clientIp(),
                    allowlist,
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
