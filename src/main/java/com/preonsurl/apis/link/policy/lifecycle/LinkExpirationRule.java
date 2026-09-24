package com.preonsurl.apis.link.policy.lifecycle;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.policy.LinkPolicyRule;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Validates that the short URL has not reached its expiration timestamp.
 */
@Component
@Order(LinkExpirationRule.ORDER)
public class LinkExpirationRule implements LinkPolicyRule {

    public static final int ORDER = 20;
    private static final Logger log = LoggerFactory.getLogger(LinkExpirationRule.class);

    private final PolicyViolationResultFactory resultFactory;

    public LinkExpirationRule(PolicyViolationResultFactory resultFactory) {
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        if (context == null) {
            return false;
        }
        return context.expireAt() != null || (context.hasUsagePolicy() && context.usagePolicy().getExpireAt() != null);
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        boolean isExpired = (context.expireAt() != null && Instant.now().isAfter(context.expireAt()))
                || (context.hasUsagePolicy() && context.usagePolicy().isExpired());

        if (isExpired) {
            Instant effectiveExpire = context.expireAt() != null
                    ? context.expireAt()
                    : context.usagePolicy().getExpireAt();

            log.warn("Link expired: id={}, url='{}', expireAt='{}'",
                    context.shortUrlId(), context.newUrl(), effectiveExpire);

            return resultFactory.expired(context.shortUrlId(), context.newUrl(), effectiveExpire, context.usagePolicy());
        }

        return null;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
