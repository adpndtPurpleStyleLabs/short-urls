package com.preonsurl.apis.link.policy;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import org.springframework.core.Ordered;

/**
 * Common contract for link serving policy rules within the Chain of Responsibility pipeline.
 * Each rule defines its own applicability, evaluation logic, and execution order.
 */
public interface LinkPolicyRule extends Ordered {

    /**
     * Determines whether this rule should be evaluated for the given context.
     *
     * @param context Immutable policy evaluation context
     * @return true if applicable, false to skip this rule
     */
    boolean isApplicable(PolicyContext context);

    /**
     * Evaluates the policy rule against the context.
     *
     * @param context Immutable policy evaluation context
     * @return PolicyEvaluationResult indicating rejection or challenge, or null / allowed to continue.
     */
    PolicyEvaluationResult evaluate(PolicyContext context);
}
