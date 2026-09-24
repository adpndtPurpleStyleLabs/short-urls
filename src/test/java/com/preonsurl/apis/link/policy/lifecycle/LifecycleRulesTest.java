package com.preonsurl.apis.link.policy.lifecycle;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.dto.PolicyEvaluationResult.ViolationType;
import com.preonsurl.apis.link.entity.UsagePolicy;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LifecycleRulesTest {

    private PolicyViolationResultFactory resultFactory;

    @BeforeEach
    void setUp() {
        resultFactory = new PolicyViolationResultFactory();
    }

    private PolicyContext createContext(
            boolean active,
            Instant expireAt,
            Long usageLimit,
            long clickCount,
            UsagePolicy usagePolicy
    ) {
        return new PolicyContext(
                100L,
                "http://localhost:8080/abc",
                "https://example.com",
                active,
                expireAt,
                usageLimit,
                clickCount,
                null,
                usagePolicy,
                null,
                "127.0.0.1",
                null,
                null,
                null,
                false,
                null,
                null,
                false
        );
    }

    @Nested
    @DisplayName("LinkActiveRule Tests")
    class ActiveRuleTests {

        private LinkActiveRule rule;

        @BeforeEach
        void setUp() {
            rule = new LinkActiveRule(resultFactory);
        }

        @Test
        void inactiveLinkReturns404Rejection() {
            PolicyContext context = createContext(false, null, null, 0, null);

            assertTrue(rule.isApplicable(context));
            PolicyEvaluationResult result = rule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.INACTIVE, result.violationType());
            assertEquals(HttpStatus.NOT_FOUND, result.httpStatus());
            assertEquals("Link Inactive", result.title());
        }

        @Test
        void activeLinkPasses() {
            PolicyContext context = createContext(true, null, null, 0, null);

            assertTrue(rule.isApplicable(context));
            assertNull(rule.evaluate(context));
        }

        @Test
        void ruleOrderIs10() {
            assertEquals(10, rule.getOrder());
        }
    }

    @Nested
    @DisplayName("LinkExpirationRule Tests")
    class ExpirationRuleTests {

        private LinkExpirationRule rule;

        @BeforeEach
        void setUp() {
            rule = new LinkExpirationRule(resultFactory);
        }

        @Test
        void expiredLinkReturns410Rejection() {
            Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
            PolicyContext context = createContext(true, past, null, 0, null);

            assertTrue(rule.isApplicable(context));
            PolicyEvaluationResult result = rule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.EXPIRED, result.violationType());
            assertEquals(HttpStatus.GONE, result.httpStatus());
            assertEquals("Link Has Expired", result.title());
            assertEquals("Expired", result.badge());
        }

        @Test
        void nonExpiredLinkPasses() {
            Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
            PolicyContext context = createContext(true, future, null, 0, null);

            assertTrue(rule.isApplicable(context));
            assertNull(rule.evaluate(context));
        }

        @Test
        void nullExpirationIsNotApplicable() {
            PolicyContext context = createContext(true, null, null, 0, null);
            assertFalse(rule.isApplicable(context));
        }

        @Test
        void ruleOrderIs20() {
            assertEquals(20, rule.getOrder());
        }
    }

    @Nested
    @DisplayName("UsageLimitRule Tests")
    class LimitRuleTests {

        private UsageLimitRule rule;

        @BeforeEach
        void setUp() {
            rule = new UsageLimitRule(resultFactory);
        }

        @Test
        void usageLimitReachedReturns410Rejection() {
            PolicyContext context = createContext(true, null, 5L, 5L, null);

            assertTrue(rule.isApplicable(context));
            PolicyEvaluationResult result = rule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.USAGE_LIMIT_EXCEEDED, result.violationType());
            assertEquals(HttpStatus.GONE, result.httpStatus());
            assertEquals("Usage Limit Reached", result.title());
            assertEquals("Limit Reached", result.badge());
        }

        @Test
        void usageLimitNotReachedPasses() {
            PolicyContext context = createContext(true, null, 5L, 4L, null);

            assertTrue(rule.isApplicable(context));
            assertNull(rule.evaluate(context));
        }

        @Test
        void nullLimitIsNotApplicable() {
            PolicyContext context = createContext(true, null, null, 10L, null);
            assertFalse(rule.isApplicable(context));
        }

        @Test
        void ruleOrderIs30() {
            assertEquals(30, rule.getOrder());
        }
    }

    @Nested
    @DisplayName("ScheduleRule Tests")
    class ScheduledRuleTests {

        private ScheduleRule rule;

        @BeforeEach
        void setUp() {
            rule = new ScheduleRule(resultFactory);
        }

        @Test
        void outsideScheduleReturns403Rejection() {
            UsagePolicy policy = new UsagePolicy();
            policy.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
            policy.setEndAt(Instant.now().plus(2, ChronoUnit.DAYS));
            PolicyContext context = createContext(true, null, null, 0, policy);

            assertTrue(rule.isApplicable(context));
            PolicyEvaluationResult result = rule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.OUTSIDE_SCHEDULE, result.violationType());
            assertEquals(HttpStatus.FORBIDDEN, result.httpStatus());
            assertEquals("Link Outside Schedule", result.title());
            assertEquals("Schedule Inactive", result.badge());
        }

        @Test
        void insideSchedulePasses() {
            UsagePolicy policy = new UsagePolicy();
            policy.setStartAt(Instant.now().minus(1, ChronoUnit.HOURS));
            policy.setEndAt(Instant.now().plus(1, ChronoUnit.HOURS));
            PolicyContext context = createContext(true, null, null, 0, policy);

            assertTrue(rule.isApplicable(context));
            assertNull(rule.evaluate(context));
        }

        @Test
        void missingScheduleIsNotApplicable() {
            PolicyContext context = createContext(true, null, null, 0, null);
            assertFalse(rule.isApplicable(context));
        }

        @Test
        void ruleOrderIs40() {
            assertEquals(40, rule.getOrder());
        }
    }
}
