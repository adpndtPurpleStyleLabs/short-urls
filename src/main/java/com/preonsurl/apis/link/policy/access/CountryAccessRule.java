package com.preonsurl.apis.link.policy.access;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.policy.LinkPolicyRule;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.evaluator.CountryPolicyEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces geographic country restrictions using client country resolution.
 */
@Component
@Order(CountryAccessRule.ORDER)
public class CountryAccessRule implements LinkPolicyRule {

    public static final int ORDER = 110;
    private static final Logger log = LoggerFactory.getLogger(CountryAccessRule.class);

    private final CountryPolicyEvaluator countryPolicyEvaluator;
    private final PolicyViolationResultFactory resultFactory;

    public CountryAccessRule(CountryPolicyEvaluator countryPolicyEvaluator, PolicyViolationResultFactory resultFactory) {
        this.countryPolicyEvaluator = countryPolicyEvaluator;
        this.resultFactory = resultFactory;
    }

    @Override
    public boolean isApplicable(PolicyContext context) {
        return context != null
                && context.isSecured()
                && context.accessPolicy().getCountries() != null
                && !context.accessPolicy().getCountries().isBlank();
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        String allowedCountries = context.accessPolicy().getCountries();
        if (!countryPolicyEvaluator.isAllowed(context.clientCountry(), allowedCountries, context.clientIp())) {
            log.warn("Access rejected by Country policy: id={}, url='{}', clientCountry='{}', allowed='{}'",
                    context.shortUrlId(), context.newUrl(), context.clientCountry(), allowedCountries);

            return resultFactory.countryRestricted(
                    context.shortUrlId(),
                    context.newUrl(),
                    context.clientCountry(),
                    allowedCountries,
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
