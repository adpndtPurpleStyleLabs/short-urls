package com.preonsurl.apis.link.policy.lifecycle;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.policy.LinkPolicyRule;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Validates that the short URL has not reached its maximum allowed access/click limit.
 */
@Component
@Order(UsageLimitRule.ORDER)
public class UsageLimitRule implements LinkPolicyRule {

    public static final int ORDER = 30;
    private static final Logger log = LoggerFactory.getLogger(UsageLimitRule.class);

    private final PolicyViolationResultFactory resultFactory;

    public UsageLimitRule(PolicyViolationResultFactory resultFactory) {
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        if (context == null) {
            return false;
        }
        return context.usageLimit() != null || (context.hasUsagePolicy() && context.usagePolicy().getUsageLimit() != null);
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        boolean isLimitReached = (context.usageLimit() != null && context.clickCount() >= context.usageLimit())
                || (context.hasUsagePolicy() && context.usagePolicy().isUsageLimitReached());

        if (isLimitReached) {
            long maxLimit = context.usageLimit() != null
                    ? context.usageLimit()
                    : context.usagePolicy().getUsageLimit();

            log.warn("Usage limit reached: id={}, url='{}', clickCount={}, limit={}",
                    context.shortUrlId(), context.newUrl(), context.clickCount(), maxLimit);

            return resultFactory.usageLimitExceeded(
                    context.shortUrlId(),
                    context.newUrl(),
                    context.clickCount(),
                    maxLimit,
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
