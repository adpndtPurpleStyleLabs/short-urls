package com.preonsurl.apis.link.policy.access;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.policy.LinkPolicyRule;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.evaluator.ReferrerPolicyEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces HTTP Referrer and allowed origin restrictions.
 */
@Component
@Order(ReferrerAccessRule.ORDER)
public class ReferrerAccessRule implements LinkPolicyRule {

    public static final int ORDER = 130;
    private static final Logger log = LoggerFactory.getLogger(ReferrerAccessRule.class);

    private final ReferrerPolicyEvaluator referrerPolicyEvaluator;
    private final PolicyViolationResultFactory resultFactory;

    public ReferrerAccessRule(ReferrerPolicyEvaluator referrerPolicyEvaluator, PolicyViolationResultFactory resultFactory) {
        this.referrerPolicyEvaluator = referrerPolicyEvaluator;
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        return context != null
                && context.isSecured()
                && context.accessPolicy().getReferrers() != null
                && !context.accessPolicy().getReferrers().isBlank();
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        String allowedReferrers = context.accessPolicy().getReferrers();
        if (!referrerPolicyEvaluator.isAllowed(context.referer(), allowedReferrers)) {
            log.warn("Access rejected by Referrer policy: id={}, url='{}', referer='{}', allowed='{}'",
                    context.shortUrlId(), context.newUrl(), context.referer(), allowedReferrers);

            return resultFactory.referrerRestricted(
                    context.shortUrlId(),
                    context.newUrl(),
                    context.referer(),
                    allowedReferrers,
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
