package com.preonsurl.apis.link.policy.access;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.dto.PolicyEvaluationResult.ViolationType;
import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.enums.AccessPolicyMode;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.evaluator.CredentialVerifier;
import com.preonsurl.apis.link.policy.security.SecurityVerificationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class CredentialAccessRuleTest {

    private PasswordEncoder passwordEncoder;
    private CredentialVerifier credentialVerifier;
    private PolicyViolationResultFactory resultFactory;
    private SecurityVerificationService securityVerificationService;
    private CredentialAccessRule credentialRule;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        credentialVerifier = new CredentialVerifier(passwordEncoder);
        resultFactory = new PolicyViolationResultFactory();
        securityVerificationService = new SecurityVerificationService();
        credentialRule = new CredentialAccessRule(credentialVerifier, resultFactory);
    }

    private PolicyContext createContext(
            AccessPolicy policy,
            boolean verifiedByCookie,
            String submittedPin,
            String submittedPassword,
            boolean isVerificationFlow
    ) {
        return new PolicyContext(
                300L,
                "http://localhost:8080/sec",
                "https://example.com/dest",
                true,
                null,
                null,
                0,
                policy,
                null,
                null,
                "127.0.0.1",
                null,
                null,
                null,
                verifiedByCookie,
                submittedPin,
                submittedPassword,
                isVerificationFlow
        );
    }

    @Nested
    @DisplayName("CredentialVerifier Direct Tests")
    class VerifierTests {

        @Test
        void pinOnlyVerification() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPinHash(passwordEncoder.encode("4321"));

            assertTrue(credentialVerifier.verify(policy, "4321", null).valid());
            assertTrue(credentialVerifier.verify(policy, " 4321 ", null).valid(), "Whitespace should be trimmed");
            assertFalse(credentialVerifier.verify(policy, "0000", null).valid());
            assertFalse(credentialVerifier.verify(policy, null, null).valid());
        }

        @Test
        void passwordOnlyVerification() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPasswordHash(passwordEncoder.encode("SecretPassword123!"));

            assertTrue(credentialVerifier.verify(policy, null, "SecretPassword123!").valid());
            assertFalse(credentialVerifier.verify(policy, null, "WrongPass").valid());
            assertFalse(credentialVerifier.verify(policy, null, null).valid());
        }

        @Test
        void pinAndPasswordBothRequired() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPinHash(passwordEncoder.encode("1234"));
            policy.setPasswordHash(passwordEncoder.encode("Pass123"));

            // Both correct
            assertTrue(credentialVerifier.verify(policy, "1234", "Pass123").valid());

            // Wrong PIN, correct Password
            CredentialVerifier.Outcome out1 = credentialVerifier.verify(policy, "0000", "Pass123");
            assertFalse(out1.valid());
            assertEquals("Invalid PIN or Password. Please check your credentials and try again.", out1.errorMessage());

            // Correct PIN, wrong Password
            CredentialVerifier.Outcome out2 = credentialVerifier.verify(policy, "1234", "Wrong");
            assertFalse(out2.valid());

            // Both wrong
            assertFalse(credentialVerifier.verify(policy, "0000", "Wrong").valid());
        }
    }

    @Nested
    @DisplayName("SecurityVerificationService Tests")
    class CookieTests {

        @Test
        void verifiedCookieDetection() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setCookies(new Cookie("PREONS_SEC_300", "VERIFIED"));
            assertTrue(securityVerificationService.isVerifiedByCookie(req, 300L));

            // Wrong URL id
            assertFalse(securityVerificationService.isVerifiedByCookie(req, 999L));

            // Wrong cookie value
            MockHttpServletRequest req2 = new MockHttpServletRequest();
            req2.setCookies(new Cookie("PREONS_SEC_300", "INVALID"));
            assertFalse(securityVerificationService.isVerifiedByCookie(req2, 300L));

            // No cookies
            assertFalse(securityVerificationService.isVerifiedByCookie(new MockHttpServletRequest(), 300L));
        }

        @Test
        void createAndClearVerificationCookie() {
            ResponseCookie cookie = securityVerificationService.createVerificationCookie(300L);
            assertEquals("PREONS_SEC_300", cookie.getName());
            assertEquals("VERIFIED", cookie.getValue());
            assertEquals(600, cookie.getMaxAge().getSeconds());
            assertTrue(cookie.isHttpOnly());

            ResponseCookie clearCookie = securityVerificationService.clearVerificationCookie(300L);
            assertEquals(0, clearCookie.getMaxAge().getSeconds());
        }
    }

    @Nested
    @DisplayName("CredentialAccessRule Serving & Challenge Tests")
    class RuleTests {

        @Test
        void getVisitWithoutCookieReturnsChallenge() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPinHash(passwordEncoder.encode("9999"));

            PolicyContext context = createContext(policy, false, null, null, false);

            assertTrue(credentialRule.isApplicable(context));
            PolicyEvaluationResult result = credentialRule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isChallengeRequired());
            assertEquals("Security Challenge Required", result.title());
        }

        @Test
        void getVisitWithVerifiedCookiePasses() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPinHash(passwordEncoder.encode("9999"));

            PolicyContext context = createContext(policy, true, null, null, false);

            assertTrue(credentialRule.isApplicable(context));
            assertNull(credentialRule.evaluate(context), "Authenticated session should pass");
        }

        @Test
        void postVerificationWithValidCredentialsPasses() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPasswordHash(passwordEncoder.encode("P@ss1234"));

            PolicyContext context = createContext(policy, false, null, "P@ss1234", true);

            assertTrue(credentialRule.isApplicable(context));
            assertNull(credentialRule.evaluate(context), "Valid submission should pass");
        }

        @Test
        void postVerificationWithInvalidCredentialsReturns401() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPasswordHash(passwordEncoder.encode("P@ss1234"));

            PolicyContext context = createContext(policy, false, null, "Wrong", true);

            assertTrue(credentialRule.isApplicable(context));
            PolicyEvaluationResult result = credentialRule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.INVALID_CREDENTIALS, result.violationType());
            assertEquals(HttpStatus.UNAUTHORIZED, result.httpStatus());
        }

        @Test
        void uncredentialedPolicyIsNotApplicable() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.PUBLIC);

            PolicyContext context = createContext(policy, false, null, null, false);
            assertFalse(credentialRule.isApplicable(context));
        }

        @Test
        void ruleOrderIs200() {
            assertEquals(200, credentialRule.getOrder());
        }
    }
}
