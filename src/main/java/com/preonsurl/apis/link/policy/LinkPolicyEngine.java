package com.preonsurl.apis.link.policy;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Central policy execution engine orchestrating LinkPolicyRule evaluations using
 * the Chain of Responsibility pattern.
 *
 * Rules are strictly executed in ascending order according to their explicit getOrder() value.
 * Execution short-circuits immediately upon the first rule rejection (DENY) or security challenge.
 */
@Service
public class LinkPolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(LinkPolicyEngine.class);

    private final List<LinkPolicyRule> orderedRules;

    public LinkPolicyEngine(List<LinkPolicyRule> rules) {
        List<LinkPolicyRule> sorted = new ArrayList<>(rules != null ? rules : List.of());
        sorted.sort(Comparator.comparingInt(LinkPolicyRule::getOrder));
        this.orderedRules = Collections.unmodifiableList(sorted);

        log.info("Initialized LinkPolicyEngine with {} rules in order: {}",
                this.orderedRules.size(),
                this.orderedRules.stream().map(r -> r.getClass().getSimpleName() + "(" + r.getOrder() + ")").toList());
    }

    /**
     * Evaluates all applicable policy rules for the provided context.
     *
     * @param context Immutable PolicyContext
     * @return PolicyEvaluationResult describing allowed status, challenge requirement, or rejection
     */
    public PolicyEvaluationResult evaluate(PolicyContext context) {
        if (context == null) {
            return PolicyEvaluationResult.notFound("New URL not found");
        }

        for (LinkPolicyRule rule : orderedRules) {
            if (!rule.isApplicable(context)) {
                continue;
            }

            PolicyEvaluationResult result = rule.evaluate(context);
            if (result != null) {
                if (result.isRejected() || result.isChallengeRequired() || result.isNotFound()) {
                    log.debug("Policy rule {} triggered decision: status={}, violation={}",
                            rule.getClass().getSimpleName(), result.status(), result.violationType());
                    return result;
                }
            }
        }

        // All applicable rules passed
        return PolicyEvaluationResult.allowed(context.accessPolicy(), context.usagePolicy());
    }

    public List<LinkPolicyRule> getOrderedRules() {
        return orderedRules;
    }
}
