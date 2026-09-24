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
 * Validates that the short URL is marked as active.
 */
@Component
@Order(LinkActiveRule.ORDER)
public class LinkActiveRule implements LinkPolicyRule {

    public static final int ORDER = 10;
    private static final Logger log = LoggerFactory.getLogger(LinkActiveRule.class);

    private final PolicyViolationResultFactory resultFactory;

    public LinkActiveRule(PolicyViolationResultFactory resultFactory) {
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        return context != null;
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        if (!context.active()) {
            log.warn("Short URL is inactive: id={}, url='{}'", context.shortUrlId(), context.newUrl());
            return resultFactory.inactive(context.shortUrlId(), context.newUrl());
        }
        return null;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
