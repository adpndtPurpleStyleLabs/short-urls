package com.preonsurl.apis.link.policy;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.dto.PolicyEvaluationResult.ViolationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinkPolicyEngineTest {

    private PolicyContext createMockContext() {
        return mock(PolicyContext.class);
    }

    @Test
    @DisplayName("Engine executes rules in strict ascending getOrder() order regardless of input order")
    void engineEnforcesAscendingOrder() {
        List<String> executionLog = new ArrayList<>();

        LinkPolicyRule rule30 = new TestRule(30, true, ctx -> {
            executionLog.add("rule30");
            return null;
        });
        LinkPolicyRule rule10 = new TestRule(10, true, ctx -> {
            executionLog.add("rule10");
            return null;
        });
        LinkPolicyRule rule20 = new TestRule(20, true, ctx -> {
            executionLog.add("rule20");
            return null;
        });

        // Pass out of order
        LinkPolicyEngine engine = new LinkPolicyEngine(List.of(rule30, rule10, rule20));
        PolicyEvaluationResult result = engine.evaluate(createMockContext());

        assertTrue(result.isAllowed());
        assertEquals(List.of("rule10", "rule20", "rule30"), executionLog);
    }

    @Test
    @DisplayName("Rule not applicable is skipped, next applicable rule executes")
    void unapplicableRulesAreSkipped() {
        List<String> executionLog = new ArrayList<>();

        LinkPolicyRule rule1 = new TestRule(10, false, ctx -> {
            executionLog.add("rule1");
            return null;
        });
        LinkPolicyRule rule2 = new TestRule(20, true, ctx -> {
            executionLog.add("rule2");
            return null;
        });

        LinkPolicyEngine engine = new LinkPolicyEngine(List.of(rule1, rule2));
        PolicyEvaluationResult result = engine.evaluate(createMockContext());

        assertTrue(result.isAllowed());
        assertEquals(List.of("rule2"), executionLog);
    }

    @Test
    @DisplayName("First rejection (DENY) immediately short-circuits and stops remaining rules")
    void firstRejectionShortCircuits() {
        List<String> executionLog = new ArrayList<>();

        LinkPolicyRule rule1 = new TestRule(10, true, ctx -> {
            executionLog.add("rule1");
            return null;
        });
        LinkPolicyRule rule2 = new TestRule(20, true, ctx -> {
            executionLog.add("rule2");
            return PolicyEvaluationResult.rejected(
                    ViolationType.EXPIRED,
                    HttpStatus.GONE,
                    "Link Expired",
                    "Expired link",
                    "Expired",
                    "No access",
                    "clock",
                    Map.of(),
                    null,
                    null
            );
        });
        LinkPolicyRule rule3 = new TestRule(30, true, ctx -> {
            executionLog.add("rule3");
            return null;
        });

        LinkPolicyEngine engine = new LinkPolicyEngine(List.of(rule1, rule2, rule3));
        PolicyEvaluationResult result = engine.evaluate(createMockContext());

        assertTrue(result.isRejected());
        assertEquals(ViolationType.EXPIRED, result.violationType());
        assertEquals(List.of("rule1", "rule2"), executionLog);
        assertFalse(executionLog.contains("rule3"), "Rule 3 must not execute after rule 2 rejection");
    }

    @Test
    @DisplayName("Challenge requirement immediately short-circuits evaluation")
    void challengeRequiredShortCircuits() {
        List<String> executionLog = new ArrayList<>();

        LinkPolicyRule rule1 = new TestRule(10, true, ctx -> {
            executionLog.add("rule1");
            return null;
        });
        LinkPolicyRule rule2 = new TestRule(20, true, ctx -> {
            executionLog.add("rule2");
            return PolicyEvaluationResult.challengeRequired(null, null);
        });
        LinkPolicyRule rule3 = new TestRule(30, true, ctx -> {
            executionLog.add("rule3");
            return null;
        });

        LinkPolicyEngine engine = new LinkPolicyEngine(List.of(rule1, rule2, rule3));
        PolicyEvaluationResult result = engine.evaluate(createMockContext());

        assertTrue(result.isChallengeRequired());
        assertEquals(List.of("rule1", "rule2"), executionLog);
        assertFalse(executionLog.contains("rule3"));
    }

    @Test
    @DisplayName("When all applicable rules pass, engine returns ALLOWED")
    void allRulesPassingReturnsAllowed() {
        LinkPolicyRule rule1 = new TestRule(10, true, ctx -> null);
        LinkPolicyRule rule2 = new TestRule(20, true, ctx -> null);

        LinkPolicyEngine engine = new LinkPolicyEngine(List.of(rule1, rule2));
        PolicyEvaluationResult result = engine.evaluate(createMockContext());

        assertTrue(result.isAllowed());
        assertEquals(HttpStatus.OK, result.httpStatus());
    }

    @Test
    @DisplayName("Null context returns NOT_FOUND")
    void nullContextReturnsNotFound() {
        LinkPolicyEngine engine = new LinkPolicyEngine(List.of());
        PolicyEvaluationResult result = engine.evaluate(null);

        assertTrue(result.isNotFound());
        assertEquals(HttpStatus.NOT_FOUND, result.httpStatus());
    }

    private static class TestRule implements LinkPolicyRule {
        private final int order;
        private final boolean applicable;
        private final java.util.function.Function<PolicyContext, PolicyEvaluationResult> action;

        public TestRule(int order, boolean applicable, java.util.function.Function<PolicyContext, PolicyEvaluationResult> action) {
            this.order = order;
            this.applicable = applicable;
            this.action = action;
        }

        @Override
        public boolean isApplicable(PolicyContext context) {
            return applicable;
        }

        @Override
        public PolicyEvaluationResult evaluate(PolicyContext context) {
            return action.apply(context);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}
