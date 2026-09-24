package com.preonsurl.apis.link.policy.access;

import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.dto.PolicyEvaluationResult.ViolationType;
import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.enums.AccessPolicyMode;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.evaluator.CidrMatcher;
import com.preonsurl.apis.link.policy.evaluator.CountryPolicyEvaluator;
import com.preonsurl.apis.link.policy.evaluator.DevicePolicyEvaluator;
import com.preonsurl.apis.link.policy.evaluator.IpPolicyEvaluator;
import com.preonsurl.apis.link.policy.evaluator.ReferrerPolicyEvaluator;
import com.preonsurl.apis.link.policy.model.DeviceInfo;
import com.preonsurl.apis.link.policy.model.DeviceOs;
import com.preonsurl.apis.link.policy.model.DeviceType;
import com.preonsurl.apis.link.policy.resolver.HeaderCountryResolver;
import com.preonsurl.apis.link.policy.resolver.TrustedProxyClientIpResolver;
import com.preonsurl.apis.link.policy.resolver.UserAgentDeviceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentalRulesAndEvaluatorsTest {

    private PolicyViolationResultFactory resultFactory;
    private CidrMatcher cidrMatcher;

    @BeforeEach
    void setUp() {
        resultFactory = new PolicyViolationResultFactory();
        cidrMatcher = new CidrMatcher();
    }

    private PolicyContext createEnvContext(
            AccessPolicy accessPolicy,
            String clientIp,
            String clientCountry,
            DeviceInfo deviceInfo,
            String referer
    ) {
        return new PolicyContext(
                200L,
                "http://localhost:8080/link",
                "https://example.com/dest",
                true,
                null,
                null,
                0,
                accessPolicy,
                null,
                null,
                clientIp,
                clientCountry,
                deviceInfo,
                referer,
                false,
                null,
                null,
                false
        );
    }

    @Nested
    @DisplayName("IP Evaluation & CIDR Matching Tests")
    class IpTests {
        private IpPolicyEvaluator ipEvaluator;
        private IpAccessRule ipRule;

        @BeforeEach
        void setUp() {
            ipEvaluator = new IpPolicyEvaluator(cidrMatcher);
            ipRule = new IpAccessRule(ipEvaluator, resultFactory);
        }

        @Test
        void exactIpv4MatchAndReject() {
            assertTrue(ipEvaluator.isAllowed("192.168.1.50", "192.168.1.50"));
            assertFalse(ipEvaluator.isAllowed("192.168.1.51", "192.168.1.50"));
        }

        @Test
        void exactIpv6MatchAndReject() {
            assertTrue(ipEvaluator.isAllowed("2001:db8::1", "2001:db8::1"));
            assertFalse(ipEvaluator.isAllowed("2001:db8::2", "2001:db8::1"));
        }

        @Test
        void ipv4CidrMatch() {
            String cidr = "10.0.0.0/16";
            assertTrue(ipEvaluator.isAllowed("10.0.0.1", cidr));
            assertTrue(ipEvaluator.isAllowed("10.0.255.254", cidr));
            assertFalse(ipEvaluator.isAllowed("10.1.0.1", cidr));
            assertFalse(ipEvaluator.isAllowed("192.168.1.1", cidr));
        }

        @Test
        void ipv6CidrMatch() {
            String cidr = "2001:db8::/32";
            assertTrue(ipEvaluator.isAllowed("2001:db8:ffff:ffff:ffff:ffff:ffff:ffff", cidr));
            assertTrue(ipEvaluator.isAllowed("2001:db8::1", cidr));
            assertFalse(ipEvaluator.isAllowed("2001:db9::1", cidr));
        }

        @Test
        void multipleAllowedRangesWithWhitespace() {
            String ranges = "192.168.1.0/24, 10.0.0.1, 2001:db8::/32";
            assertTrue(ipEvaluator.isAllowed("192.168.1.42", ranges));
            assertTrue(ipEvaluator.isAllowed("10.0.0.1", ranges));
            assertTrue(ipEvaluator.isAllowed("2001:db8:1::1", ranges));
            assertFalse(ipEvaluator.isAllowed("172.16.0.1", ranges));
        }

        @Test
        void invalidIpAndInvalidCidrHandledGracefully() {
            assertFalse(ipEvaluator.isAllowed("not-an-ip", "10.0.0.0/8"));
            assertFalse(ipEvaluator.isAllowed("10.0.0.1", "invalid-cidr-format"));
            assertFalse(ipEvaluator.isAllowed("10.0.0.1", "10.0.0.0/999"));
            assertFalse(ipEvaluator.isAllowed(null, "10.0.0.0/8"));
        }

        @Test
        void emptyPolicyAllowsAll() {
            assertTrue(ipEvaluator.isAllowed("1.2.3.4", null));
            assertTrue(ipEvaluator.isAllowed("1.2.3.4", "   "));
        }

        @Test
        void ruleEvaluatesAndReturnsIpRestrictedResult() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setIpAllowlist("10.0.0.0/8");
            PolicyContext context = createEnvContext(policy, "192.168.1.1", null, null, null);

            assertTrue(ipRule.isApplicable(context));
            PolicyEvaluationResult result = ipRule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.IP_RESTRICTED, result.violationType());
            assertEquals(HttpStatus.FORBIDDEN, result.httpStatus());
            assertEquals("IP Restricted", result.badge());
            assertEquals("192.168.1.1", result.details().get("Your IP Address"));
        }

        @Test
        void trustedProxyClientIpResolverCorrectlyExtractsIp() {
            TrustedProxyClientIpResolver resolver = new TrustedProxyClientIpResolver(cidrMatcher, "127.0.0.1, ::1", false);

            // Trusted proxy with X-Forwarded-For
            MockHttpServletRequest trustedReq = new MockHttpServletRequest();
            trustedReq.setRemoteAddr("127.0.0.1");
            trustedReq.addHeader("X-Forwarded-For", "203.0.113.195, 10.0.0.1");
            assertEquals("203.0.113.195", resolver.resolveClientIp(trustedReq));

            // Untrusted remote address: ignores forged X-Forwarded-For!
            MockHttpServletRequest untrustedReq = new MockHttpServletRequest();
            untrustedReq.setRemoteAddr("198.51.100.22");
            untrustedReq.addHeader("X-Forwarded-For", "203.0.113.195");
            assertEquals("198.51.100.22", resolver.resolveClientIp(untrustedReq));
        }

        @Test
        void ruleOrderIs100() {
            assertEquals(100, ipRule.getOrder());
        }
    }

    @Nested
    @DisplayName("Country Evaluation & Header Resolution Tests")
    class CountryTests {
        private CountryPolicyEvaluator countryEvaluator;
        private CountryAccessRule countryRule;
        private HeaderCountryResolver countryResolver;

        @BeforeEach
        void setUp() {
            countryEvaluator = new CountryPolicyEvaluator(cidrMatcher);
            countryRule = new CountryAccessRule(countryEvaluator, resultFactory);
            countryResolver = new HeaderCountryResolver();
        }

        @Test
        void allowedAndBlockedCountry() {
            String allowed = "US, IN, GB";
            assertTrue(countryEvaluator.isAllowed("US", allowed, "203.0.113.1"));
            assertTrue(countryEvaluator.isAllowed("in", allowed, "203.0.113.1"));
            assertTrue(countryEvaluator.isAllowed("GB", allowed, "203.0.113.1"));
            assertFalse(countryEvaluator.isAllowed("FR", allowed, "203.0.113.1"));
            assertFalse(countryEvaluator.isAllowed("DE", allowed, "203.0.113.1"));
        }

        @Test
        void missingCountryFailsUnlessLocalIp() {
            String allowed = "US, GB";
            // Public IP with missing country -> blocked
            assertFalse(countryEvaluator.isAllowed(null, allowed, "203.0.113.1"));
            assertFalse(countryEvaluator.isAllowed("", allowed, "203.0.113.1"));

            // Local loopback / private network with missing country -> allowed for local dev
            assertTrue(countryEvaluator.isAllowed(null, allowed, "127.0.0.1"));
            assertTrue(countryEvaluator.isAllowed(null, allowed, "192.168.1.15"));
        }

        @Test
        void emptyPolicyAllowsAll() {
            assertTrue(countryEvaluator.isAllowed("FR", null, "203.0.113.1"));
            assertTrue(countryEvaluator.isAllowed("FR", "  ", "203.0.113.1"));
        }

        @Test
        void headerResolverExtractsFromVariousCdnHeaders() {
            MockHttpServletRequest cfReq = new MockHttpServletRequest();
            cfReq.addHeader("CF-IPCountry", "in");
            assertEquals("IN", countryResolver.resolveCountry(cfReq));

            MockHttpServletRequest cloudfrontReq = new MockHttpServletRequest();
            cloudfrontReq.addHeader("CloudFront-Viewer-Country", "US");
            assertEquals("US", countryResolver.resolveCountry(cloudfrontReq));

            MockHttpServletRequest nginxReq = new MockHttpServletRequest();
            nginxReq.addHeader("GEOIP-COUNTRY-CODE", "GB");
            assertEquals("GB", countryResolver.resolveCountry(nginxReq));

            MockHttpServletRequest emptyReq = new MockHttpServletRequest();
            assertNull(countryResolver.resolveCountry(emptyReq));
        }

        @Test
        void ruleEvaluatesAndReturnsCountryRestrictedResult() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setCountries("US, CA");
            PolicyContext context = createEnvContext(policy, "203.0.113.1", "FR", null, null);

            assertTrue(countryRule.isApplicable(context));
            PolicyEvaluationResult result = countryRule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.COUNTRY_RESTRICTED, result.violationType());
            assertEquals("Country Blocked", result.badge());
            assertEquals("FR", result.details().get("Detected Country"));
            assertEquals("US, CA", result.details().get("Allowed Countries"));
        }

        @Test
        void ruleOrderIs110() {
            assertEquals(110, countryRule.getOrder());
        }
    }

    @Nested
    @DisplayName("Device Evaluation & Classification Tests")
    class DeviceTests {
        private DevicePolicyEvaluator deviceEvaluator;
        private DeviceAccessRule deviceRule;
        private UserAgentDeviceResolver deviceResolver;

        @BeforeEach
        void setUp() {
            deviceEvaluator = new DevicePolicyEvaluator();
            deviceRule = new DeviceAccessRule(deviceEvaluator, resultFactory);
            deviceResolver = new UserAgentDeviceResolver();
        }

        @Test
        void mobileTabletDesktopClassification() {
            DeviceInfo mobile = new DeviceInfo(DeviceType.MOBILE, DeviceOs.IOS, "Mobile (iOS)");
            DeviceInfo tablet = new DeviceInfo(DeviceType.TABLET, DeviceOs.ANDROID, "Tablet (Android)");
            DeviceInfo desktop = new DeviceInfo(DeviceType.DESKTOP, DeviceOs.MACOS, "Desktop (macOS)");

            assertTrue(deviceEvaluator.isAllowed(mobile, "MOBILE"));
            assertFalse(deviceEvaluator.isAllowed(desktop, "MOBILE"));

            assertTrue(deviceEvaluator.isAllowed(tablet, "TABLET"));
            assertFalse(deviceEvaluator.isAllowed(mobile, "TABLET"));

            assertTrue(deviceEvaluator.isAllowed(desktop, "DESKTOP"));
            assertFalse(deviceEvaluator.isAllowed(mobile, "DESKTOP"));
        }

        @Test
        void specificOsFiltering() {
            DeviceInfo ios = new DeviceInfo(DeviceType.MOBILE, DeviceOs.IOS, "Mobile (iOS)");
            DeviceInfo android = new DeviceInfo(DeviceType.MOBILE, DeviceOs.ANDROID, "Mobile (Android)");
            DeviceInfo windows = new DeviceInfo(DeviceType.DESKTOP, DeviceOs.WINDOWS, "Desktop (Windows)");

            assertTrue(deviceEvaluator.isAllowed(ios, "IOS, ANDROID"));
            assertTrue(deviceEvaluator.isAllowed(android, "IOS, ANDROID"));
            assertFalse(deviceEvaluator.isAllowed(windows, "IOS, ANDROID"));
        }

        @Test
        void userAgentResolverDetectsAllTargetPlatforms() {
            // iOS iPhone
            MockHttpServletRequest iphoneReq = new MockHttpServletRequest();
            iphoneReq.addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1");
            DeviceInfo iphone = deviceResolver.resolveDevice(iphoneReq);
            assertEquals(DeviceType.MOBILE, iphone.type());
            assertEquals(DeviceOs.IOS, iphone.os());

            // iPad Tablet
            MockHttpServletRequest ipadReq = new MockHttpServletRequest();
            ipadReq.addHeader("User-Agent", "Mozilla/5.0 (iPad; CPU OS 16_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1");
            DeviceInfo ipad = deviceResolver.resolveDevice(ipadReq);
            assertEquals(DeviceType.TABLET, ipad.type());
            assertEquals(DeviceOs.IOS, ipad.os());

            // Android Mobile
            MockHttpServletRequest androidReq = new MockHttpServletRequest();
            androidReq.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Mobile Safari/537.36");
            DeviceInfo androidDev = deviceResolver.resolveDevice(androidReq);
            assertEquals(DeviceType.MOBILE, androidDev.type());
            assertEquals(DeviceOs.ANDROID, androidDev.os());

            // Windows Desktop
            MockHttpServletRequest winReq = new MockHttpServletRequest();
            winReq.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            DeviceInfo winDev = deviceResolver.resolveDevice(winReq);
            assertEquals(DeviceType.DESKTOP, winDev.type());
            assertEquals(DeviceOs.WINDOWS, winDev.os());

            // macOS Desktop
            MockHttpServletRequest macReq = new MockHttpServletRequest();
            macReq.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            DeviceInfo macDev = deviceResolver.resolveDevice(macReq);
            assertEquals(DeviceType.DESKTOP, macDev.type());
            assertEquals(DeviceOs.MACOS, macDev.os());

            // Linux Desktop
            MockHttpServletRequest linuxReq = new MockHttpServletRequest();
            linuxReq.addHeader("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/119.0");
            DeviceInfo linuxDev = deviceResolver.resolveDevice(linuxReq);
            assertEquals(DeviceType.DESKTOP, linuxDev.type());
            assertEquals(DeviceOs.LINUX, linuxDev.os());

            // Unknown / Missing UA
            MockHttpServletRequest emptyReq = new MockHttpServletRequest();
            DeviceInfo emptyDev = deviceResolver.resolveDevice(emptyReq);
            assertEquals(DeviceType.OTHER, emptyDev.type());
        }

        @Test
        void ruleEvaluatesAndReturnsDeviceRestrictedResult() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setDeviceTypes("MOBILE");
            DeviceInfo desktop = new DeviceInfo(DeviceType.DESKTOP, DeviceOs.MACOS, "Desktop (macOS)");
            PolicyContext context = createEnvContext(policy, "127.0.0.1", null, desktop, null);

            assertTrue(deviceRule.isApplicable(context));
            PolicyEvaluationResult result = deviceRule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.DEVICE_RESTRICTED, result.violationType());
            assertEquals("Device Incompatible", result.badge());
            assertEquals("Desktop (macOS)", result.details().get("Detected Device"));
        }

        @Test
        void ruleOrderIs120() {
            assertEquals(120, deviceRule.getOrder());
        }
    }

    @Nested
    @DisplayName("Referrer Evaluation & Boundary Safety Tests")
    class ReferrerTests {
        private ReferrerPolicyEvaluator referrerEvaluator;
        private ReferrerAccessRule referrerRule;

        @BeforeEach
        void setUp() {
            referrerEvaluator = new ReferrerPolicyEvaluator();
            referrerRule = new ReferrerAccessRule(referrerEvaluator, resultFactory);
        }

        @Test
        void exactDomainMatch() {
            String allowed = "example.com";
            assertTrue(referrerEvaluator.isAllowed("https://example.com/checkout", allowed));
            assertTrue(referrerEvaluator.isAllowed("http://example.com", allowed));
            assertTrue(referrerEvaluator.isAllowed("https://sub.example.com/page", allowed));
        }

        @Test
        void wildcardSubdomainMatch() {
            String allowed = "*.mycorp.internal";
            assertTrue(referrerEvaluator.isAllowed("https://wiki.mycorp.internal/spec", allowed));
            assertTrue(referrerEvaluator.isAllowed("https://api.v2.mycorp.internal/spec", allowed));
            assertTrue(referrerEvaluator.isAllowed("https://mycorp.internal/home", allowed));
            assertFalse(referrerEvaluator.isAllowed("https://othercorp.internal/spec", allowed));
        }

        @Test
        void maliciousLookalikeDomainsAreSafelyBlocked() {
            String allowed = "example.com";

            // Suffix lookalike: evil-example.com (must NOT match example.com!)
            assertFalse(referrerEvaluator.isAllowed("https://evil-example.com/phish", allowed));

            // Prefix lookalike: notexample.com (must NOT match example.com!)
            assertFalse(referrerEvaluator.isAllowed("https://notexample.com", allowed));

            // Subdomain spoof: example.com.attacker.com (must NOT match example.com!)
            assertFalse(referrerEvaluator.isAllowed("https://example.com.attacker.com/steal", allowed));

            // Similar domain: myexample.com
            assertFalse(referrerEvaluator.isAllowed("https://myexample.com", allowed));
        }

        @Test
        void missingAndInvalidReferrerBlockedWhenPolicyConfigured() {
            String allowed = "example.com";
            assertFalse(referrerEvaluator.isAllowed(null, allowed));
            assertFalse(referrerEvaluator.isAllowed("", allowed));
            assertFalse(referrerEvaluator.isAllowed("invalid-uri-scheme$$$", allowed));
        }

        @Test
        void urlPrefixMatchingWithBoundary() {
            String allowed = "https://partner.com/campaign";
            assertTrue(referrerEvaluator.isAllowed("https://partner.com/campaign", allowed));
            assertTrue(referrerEvaluator.isAllowed("https://partner.com/campaign/promo1", allowed));
            assertTrue(referrerEvaluator.isAllowed("https://partner.com/campaign?src=email", allowed));

            // Must NOT match https://partner.com/campaign-fraud
            assertFalse(referrerEvaluator.isAllowed("https://partner.com/campaign-fraud", allowed));
        }

        @Test
        void emptyPolicyAllowsAll() {
            assertTrue(referrerEvaluator.isAllowed("https://anywhere.com", null));
            assertTrue(referrerEvaluator.isAllowed(null, ""));
        }

        @Test
        void ruleEvaluatesAndReturnsReferrerRestrictedResult() {
            AccessPolicy policy = new AccessPolicy();
            policy.setMode(AccessPolicyMode.SECURED);
            policy.setReferrers("partner.com");
            PolicyContext context = createEnvContext(policy, "127.0.0.1", null, null, null); // null referer

            assertTrue(referrerRule.isApplicable(context));
            PolicyEvaluationResult result = referrerRule.evaluate(context);

            assertNotNull(result);
            assertTrue(result.isRejected());
            assertEquals(ViolationType.REFERRER_RESTRICTED, result.violationType());
            assertEquals("Referrer Blocked", result.badge());
            assertEquals("Direct Access (No Referer)", result.details().get("Detected Referrer"));
        }

        @Test
        void ruleOrderIs130() {
            assertEquals(130, referrerRule.getOrder());
        }
    }
}
