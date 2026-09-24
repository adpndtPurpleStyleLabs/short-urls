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
 * Validates that current time falls within the configured startAt / endAt schedule window.
 */
@Component
@Order(ScheduleRule.ORDER)
public class ScheduleRule implements LinkPolicyRule {

    public static final int ORDER = 40;
    private static final Logger log = LoggerFactory.getLogger(ScheduleRule.class);

    private final PolicyViolationResultFactory resultFactory;

    public ScheduleRule(PolicyViolationResultFactory resultFactory) {
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        return context != null && context.hasUsagePolicy() && context.usagePolicy().getStartAt() != null;
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        if (context.usagePolicy().isOutsideSchedule()) {
            log.warn("Link outside scheduled access window: id={}, url='{}', startAt='{}', endAt='{}'",
                    context.shortUrlId(), context.newUrl(),
                    context.usagePolicy().getStartAt(), context.usagePolicy().getEndAt());

            return resultFactory.outsideSchedule(
                    context.shortUrlId(),
                    context.newUrl(),
                    context.usagePolicy().getStartAt(),
                    context.usagePolicy().getEndAt(),
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
