package com.preonsurl.apis.link;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.dto.PolicyEvaluationResult.ViolationType;
import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.UsagePolicy;
import com.preonsurl.apis.link.enums.AccessPolicyMode;
import com.preonsurl.apis.link.policy.LinkPolicyEngine;
import com.preonsurl.apis.link.policy.PolicyContextFactory;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.access.*;
import com.preonsurl.apis.link.policy.evaluator.*;
import com.preonsurl.apis.link.policy.lifecycle.*;
import com.preonsurl.apis.link.policy.resolver.*;
import com.preonsurl.apis.link.policy.security.SecurityVerificationService;
import com.preonsurl.apis.link.repository.AccessPolicyRepository;
import com.preonsurl.apis.link.repository.UsagePolicyRepository;
import com.preonsurl.apis.link.service.LinkAccessAndUsageService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * End-to-end pipeline tests for LinkAccessAndUsageService facade and the complete policy engine.
 * Validates Request -> PolicyContextFactory -> LinkPolicyEngine -> Rules -> PolicyEvaluationResult.
 */
@ExtendWith(MockitoExtension.class)
class LinkAccessAndUsageServiceTest {

    @Mock
    private AccessPolicyRepository accessPolicyRepository;

    @Mock
    private UsagePolicyRepository usagePolicyRepository;

    private PasswordEncoder passwordEncoder;
    private LinkAccessAndUsageService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        CidrMatcher cidrMatcher = new CidrMatcher();
        PolicyViolationResultFactory resultFactory = new PolicyViolationResultFactory();

        // Resolvers
        ClientIpResolver ipResolver = new TrustedProxyClientIpResolver(cidrMatcher, "127.0.0.1, ::1", false);
        CountryResolver countryResolver = new HeaderCountryResolver();
        DeviceResolver deviceResolver = new UserAgentDeviceResolver();
        SecurityVerificationService securityService = new SecurityVerificationService();

        // Evaluators
        IpPolicyEvaluator ipEvaluator = new IpPolicyEvaluator(cidrMatcher);
        CountryPolicyEvaluator countryEvaluator = new CountryPolicyEvaluator(cidrMatcher);
        DevicePolicyEvaluator deviceEvaluator = new DevicePolicyEvaluator();
        ReferrerPolicyEvaluator referrerEvaluator = new ReferrerPolicyEvaluator();
        CredentialVerifier credentialVerifier = new CredentialVerifier(passwordEncoder);

        // Rules in Order
        LinkActiveRule activeRule = new LinkActiveRule(resultFactory);
        LinkExpirationRule expirationRule = new LinkExpirationRule(resultFactory);
        UsageLimitRule limitRule = new UsageLimitRule(resultFactory);
        ScheduleRule scheduleRule = new ScheduleRule(resultFactory);
        IpAccessRule ipRule = new IpAccessRule(ipEvaluator, resultFactory);
        CountryAccessRule countryRule = new CountryAccessRule(countryEvaluator, resultFactory);
        DeviceAccessRule deviceRule = new DeviceAccessRule(deviceEvaluator, resultFactory);
        ReferrerAccessRule referrerRule = new ReferrerAccessRule(referrerEvaluator, resultFactory);
        CredentialAccessRule credentialRule = new CredentialAccessRule(credentialVerifier, resultFactory);

        LinkPolicyEngine engine = new LinkPolicyEngine(List.of(
                activeRule, expirationRule, limitRule, scheduleRule,
                ipRule, countryRule, deviceRule, referrerRule, credentialRule
        ));

        PolicyContextFactory contextFactory = new PolicyContextFactory(
                accessPolicyRepository,
                usagePolicyRepository,
                ipResolver,
                countryResolver,
                deviceResolver,
                securityService
        );

        service = new LinkAccessAndUsageService(contextFactory, engine, ipResolver, securityService);
    }

    private NewUrl createBaseEntity(Long id) {
        NewUrl entity = new NewUrl();
        entity.setId(id);
        entity.setShortCode("testCode" + id);
        entity.setNewUrl("http://localhost:8080/testCode" + id);
        entity.setOriginalUrl("https://example.com/target");
        entity.setActive(true);
        return entity;
    }

    @Nested
    @DisplayName("IP Allowlist Pipeline Tests")
    class IpAllowlistPipelineTests {

        @Test
        void singleExactIpAllowed() {
            NewUrl entity = createBaseEntity(1L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setIpAllowlist("203.0.113.10");
            when(accessPolicyRepository.findByShortUrlId(1L)).thenReturn(Optional.of(policy));

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("203.0.113.10");

            PolicyEvaluationResult result = service.evaluatePolicies(entity, request);
            assertTrue(result.isAllowed());

            // Wrong IP
            request.setRemoteAddr("203.0.113.11");
            PolicyEvaluationResult rejected = service.evaluatePolicies(entity, request);
            assertTrue(rejected.isRejected());
            assertEquals(ViolationType.IP_RESTRICTED, rejected.violationType());
            assertEquals(HttpStatus.FORBIDDEN, rejected.httpStatus());
            assertTrue(rejected.description().contains("203.0.113.11"));
        }

        @Test
        void cidrSubnetIpAllowed() {
            NewUrl entity = createBaseEntity(2L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setIpAllowlist("10.0.0.0/16, 192.168.1.0/24");
            when(accessPolicyRepository.findByShortUrlId(2L)).thenReturn(Optional.of(policy));

            // Within 10.0.0.0/16
            MockHttpServletRequest request1 = new MockHttpServletRequest();
            request1.setRemoteAddr("10.0.45.67");
            assertTrue(service.evaluatePolicies(entity, request1).isAllowed());

            // Within 192.168.1.0/24
            MockHttpServletRequest request2 = new MockHttpServletRequest();
            request2.setRemoteAddr("192.168.1.254");
            assertTrue(service.evaluatePolicies(entity, request2).isAllowed());

            // Outside both subnets
            MockHttpServletRequest request3 = new MockHttpServletRequest();
            request3.setRemoteAddr("192.168.2.1");
            PolicyEvaluationResult rejected = service.evaluatePolicies(entity, request3);
            assertTrue(rejected.isRejected());
            assertEquals(ViolationType.IP_RESTRICTED, rejected.violationType());
        }

        @Test
        void ipv6CidrSupported() {
            NewUrl entity = createBaseEntity(3L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setIpAllowlist("2001:db8::/32");
            when(accessPolicyRepository.findByShortUrlId(3L)).thenReturn(Optional.of(policy));

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("2001:db8:0:0:0:0:0:1");
            assertTrue(service.evaluatePolicies(entity, request).isAllowed());

            request.setRemoteAddr("2001:db9::1");
            assertTrue(service.evaluatePolicies(entity, request).isRejected());
        }

        @Test
        void xForwardedForHeaderExtractsClientIpWhenBehindTrustedProxy() {
            NewUrl entity = createBaseEntity(4L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setIpAllowlist("198.51.100.5");
            when(accessPolicyRepository.findByShortUrlId(4L)).thenReturn(Optional.of(policy));

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("127.0.0.1"); // Trusted proxy
            request.addHeader("X-Forwarded-For", "198.51.100.5, 10.0.0.1");

            PolicyEvaluationResult result = service.evaluatePolicies(entity, request);
            assertTrue(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("Country Geofencing Pipeline Tests")
    class CountryGeofencingPipelineTests {

        @Test
        void allowedCountrySucceeds() {
            NewUrl entity = createBaseEntity(10L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setCountries("US, IN, CA");
            when(accessPolicyRepository.findByShortUrlId(10L)).thenReturn(Optional.of(policy));

            // CF-IPCountry: IN
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("203.0.113.1");
            request.addHeader("CF-IPCountry", "IN");
            assertTrue(service.evaluatePolicies(entity, request).isAllowed());

            // CloudFront-Viewer-Country: us (case-insensitive)
            MockHttpServletRequest request2 = new MockHttpServletRequest();
            request2.setRemoteAddr("203.0.113.1");
            request2.addHeader("CloudFront-Viewer-Country", "us");
            assertTrue(service.evaluatePolicies(entity, request2).isAllowed());
        }

        @Test
        void rejectedCountryReturnsInformativeRejection() {
            NewUrl entity = createBaseEntity(11L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setCountries("US, GB");
            when(accessPolicyRepository.findByShortUrlId(11L)).thenReturn(Optional.of(policy));

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("203.0.113.1");
            request.addHeader("X-Country-Code", "FR");

            PolicyEvaluationResult result = service.evaluatePolicies(entity, request);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.COUNTRY_RESTRICTED, result.violationType());
            assertEquals(HttpStatus.FORBIDDEN, result.httpStatus());
            assertEquals("Country Blocked", result.badge());
            assertTrue(result.details().containsKey("Detected Country"));
            assertTrue(result.details().containsKey("Allowed Countries"));
        }
    }

    @Nested
    @DisplayName("Device Restriction Pipeline Tests")
    class DeviceRestrictionPipelineTests {

        @Test
        void mobileDeviceFilterAcceptsMobileAndRejectsDesktop() {
            NewUrl entity = createBaseEntity(20L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setDeviceTypes("MOBILE");
            when(accessPolicyRepository.findByShortUrlId(20L)).thenReturn(Optional.of(policy));

            // Mobile User-Agent
            MockHttpServletRequest mobileRequest = new MockHttpServletRequest();
            mobileRequest.addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1");
            assertTrue(service.evaluatePolicies(entity, mobileRequest).isAllowed());

            // Desktop User-Agent
            MockHttpServletRequest desktopRequest = new MockHttpServletRequest();
            desktopRequest.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            PolicyEvaluationResult rejected = service.evaluatePolicies(entity, desktopRequest);
            assertTrue(rejected.isRejected());
            assertEquals(ViolationType.DEVICE_RESTRICTED, rejected.violationType());
            assertEquals(HttpStatus.FORBIDDEN, rejected.httpStatus());
            assertEquals("Device Incompatible", rejected.badge());
            assertEquals("Desktop (macOS)", rejected.details().get("Detected Device"));
        }

        @Test
        void specificOsFilterAllowsTargetOsOnly() {
            NewUrl entity = createBaseEntity(21L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setDeviceTypes("IOS, ANDROID");
            when(accessPolicyRepository.findByShortUrlId(21L)).thenReturn(Optional.of(policy));

            // Android
            MockHttpServletRequest androidReq = new MockHttpServletRequest();
            androidReq.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Mobile Safari/537.36");
            assertTrue(service.evaluatePolicies(entity, androidReq).isAllowed());

            // Windows desktop
            MockHttpServletRequest winReq = new MockHttpServletRequest();
            winReq.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Edge/120.0.0.0");
            PolicyEvaluationResult result = service.evaluatePolicies(entity, winReq);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.DEVICE_RESTRICTED, result.violationType());
        }
    }

    @Nested
    @DisplayName("Referrer Restriction Pipeline Tests")
    class ReferrerRestrictionPipelineTests {

        @Test
        void exactAndWildcardReferrersAllowed() {
            NewUrl entity = createBaseEntity(30L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setReferrers("*.mycorp.internal, partner.com");
            when(accessPolicyRepository.findByShortUrlId(30L)).thenReturn(Optional.of(policy));

            // Wildcard subdomain
            MockHttpServletRequest req1 = new MockHttpServletRequest();
            req1.addHeader("Referer", "https://wiki.mycorp.internal/docs/spec");
            assertTrue(service.evaluatePolicies(entity, req1).isAllowed());

            // Root of wildcard domain
            MockHttpServletRequest req2 = new MockHttpServletRequest();
            req2.addHeader("Referer", "https://mycorp.internal/home");
            assertTrue(service.evaluatePolicies(entity, req2).isAllowed());

            // Exact match
            MockHttpServletRequest req3 = new MockHttpServletRequest();
            req3.addHeader("Referer", "https://partner.com/campaign");
            assertTrue(service.evaluatePolicies(entity, req3).isAllowed());

            // Unauthorized external referer
            MockHttpServletRequest req4 = new MockHttpServletRequest();
            req4.addHeader("Referer", "https://unauthorized.com/link");
            PolicyEvaluationResult rejected = service.evaluatePolicies(entity, req4);
            assertTrue(rejected.isRejected());
            assertEquals(ViolationType.REFERRER_RESTRICTED, rejected.violationType());
            assertEquals("Referrer Blocked", rejected.badge());

            // Missing referer
            MockHttpServletRequest req5 = new MockHttpServletRequest();
            PolicyEvaluationResult missingReferer = service.evaluatePolicies(entity, req5);
            assertTrue(missingReferer.isRejected());
            assertEquals(ViolationType.REFERRER_RESTRICTED, missingReferer.violationType());
            assertEquals("Direct Access (No Referer)", missingReferer.details().get("Detected Referrer"));
        }
    }

    @Nested
    @DisplayName("PIN and Password Pipeline Tests")
    class CredentialsPipelineTests {

        @Test
        void pinSecuredRequiresChallengeUnlessCookiePresent() {
            NewUrl entity = createBaseEntity(40L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPinHash(passwordEncoder.encode("4321"));
            when(accessPolicyRepository.findByShortUrlId(40L)).thenReturn(Optional.of(policy));

            // First visit without cookie
            MockHttpServletRequest request = new MockHttpServletRequest();
            PolicyEvaluationResult challenge = service.evaluatePolicies(entity, request);
            assertTrue(challenge.isChallengeRequired());

            // Re-visit with valid PREONS_SEC_{id} cookie
            request.setCookies(new Cookie("PREONS_SEC_40", "VERIFIED"));
            PolicyEvaluationResult verified = service.evaluatePolicies(entity, request);
            assertTrue(verified.isAllowed());
        }

        @Test
        void verifyCredentialsValidatesPinSuccessfully() {
            NewUrl entity = createBaseEntity(41L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPinHash(passwordEncoder.encode("1234"));
            when(accessPolicyRepository.findByShortUrlId(41L)).thenReturn(Optional.of(policy));

            MockHttpServletRequest request = new MockHttpServletRequest();

            // Correct PIN
            PolicyEvaluationResult success = service.verifyCredentials(entity, "1234", null, request);
            assertTrue(success.isAllowed());

            // Incorrect PIN
            PolicyEvaluationResult failure = service.verifyCredentials(entity, "9999", null, request);
            assertTrue(failure.isRejected());
            assertEquals(ViolationType.INVALID_CREDENTIALS, failure.violationType());
            assertEquals(HttpStatus.UNAUTHORIZED, failure.httpStatus());
        }

        @Test
        void verifyCredentialsValidatesPasswordSuccessfully() {
            NewUrl entity = createBaseEntity(42L);
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setPasswordHash(passwordEncoder.encode("SuperSecret99!"));
            when(accessPolicyRepository.findByShortUrlId(42L)).thenReturn(Optional.of(policy));

            MockHttpServletRequest request = new MockHttpServletRequest();

            // Correct Password
            assertTrue(service.verifyCredentials(entity, null, "SuperSecret99!", request).isAllowed());

            // Incorrect Password
            PolicyEvaluationResult failure = service.verifyCredentials(entity, null, "WrongPass", request);
            assertTrue(failure.isRejected());
            assertEquals(ViolationType.INVALID_CREDENTIALS, failure.violationType());
        }
    }

    @Nested
    @DisplayName("Usage Policies and Lifecycle Pipeline Tests")
    class UsageLifecyclePipelineTests {

        @Test
        void inactiveUrlRejectedWith404() {
            NewUrl entity = createBaseEntity(50L);
            entity.setActive(false);

            MockHttpServletRequest request = new MockHttpServletRequest();
            PolicyEvaluationResult result = service.evaluatePolicies(entity, request);

            assertTrue(result.isRejected());
            assertEquals(ViolationType.INACTIVE, result.violationType());
            assertEquals(HttpStatus.NOT_FOUND, result.httpStatus());
            assertEquals("Link Inactive", result.title());
        }

        @Test
        void expiredUrlRejectedWith410Gone() {
            NewUrl entity = createBaseEntity(51L);
            entity.setExpireAt(Instant.now().minus(1, ChronoUnit.HOURS));

            MockHttpServletRequest request = new MockHttpServletRequest();
            PolicyEvaluationResult result = service.evaluatePolicies(entity, request);

            assertTrue(result.isRejected());
            assertEquals(ViolationType.EXPIRED, result.violationType());
            assertEquals(HttpStatus.GONE, result.httpStatus());
            assertEquals("Link Has Expired", result.title());
            assertEquals("Expired", result.badge());
        }

        @Test
        void usageLimitReachedRejectedWith410Gone() {
            NewUrl entity = createBaseEntity(52L);
            entity.setUsageLimit(5L);
            entity.setClickCount(5L);

            MockHttpServletRequest request = new MockHttpServletRequest();
            PolicyEvaluationResult result = service.evaluatePolicies(entity, request);

            assertTrue(result.isRejected());
            assertEquals(ViolationType.USAGE_LIMIT_EXCEEDED, result.violationType());
            assertEquals(HttpStatus.GONE, result.httpStatus());
            assertEquals("Usage Limit Reached", result.title());
            assertEquals("Limit Reached", result.badge());
        }

        @Test
        void outsideScheduleRejectedWith403Forbidden() {
            NewUrl entity = createBaseEntity(53L);
            UsagePolicy usagePolicy = new UsagePolicy();
            usagePolicy.setStartAt(Instant.now().plus(2, ChronoUnit.DAYS));
            usagePolicy.setEndAt(Instant.now().plus(4, ChronoUnit.DAYS));
            when(usagePolicyRepository.findByShortUrlId(53L)).thenReturn(Optional.of(usagePolicy));

            MockHttpServletRequest request = new MockHttpServletRequest();
            PolicyEvaluationResult result = service.evaluatePolicies(entity, request);

            assertTrue(result.isRejected());
            assertEquals(ViolationType.OUTSIDE_SCHEDULE, result.violationType());
            assertEquals(HttpStatus.FORBIDDEN, result.httpStatus());
            assertEquals("Link Outside Schedule", result.title());
            assertEquals("Schedule Inactive", result.badge());
        }
    }
}
