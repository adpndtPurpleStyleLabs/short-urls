package com.preonsurl.apis.link.policy.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preonsurl.apis.link.dto.CreateRequest.AccessPolicies.GeoFencePolicy;
import com.preonsurl.apis.link.dto.PolicyEvaluationResult;
import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.enums.AccessPolicyMode;
import com.preonsurl.apis.link.policy.PolicyContext;
import com.preonsurl.apis.link.policy.PolicyViolationResultFactory;
import com.preonsurl.apis.link.policy.evaluator.CidrMatcher;
import com.preonsurl.apis.link.policy.evaluator.GeoFencePolicyEvaluator;
import com.preonsurl.apis.link.policy.model.GeoCoordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoFenceAccessRuleTest {

    private ObjectMapper objectMapper;
    private CidrMatcher cidrMatcher;
    private GeoFencePolicyEvaluator evaluator;
    private PolicyViolationResultFactory resultFactory;
    private GeoFenceAccessRule rule;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cidrMatcher = new CidrMatcher();
        evaluator = new GeoFencePolicyEvaluator(objectMapper, cidrMatcher);
        resultFactory = new PolicyViolationResultFactory();
        rule = new GeoFenceAccessRule(evaluator, resultFactory);
    }

    private PolicyContext createContext(AccessPolicy policy, GeoCoordinates coordinates, String clientIp) {
        return new PolicyContext(
                100L,
                "http://localhost:8080/fence-test",
                "https://example.com/target",
                true,
                null,
                null,
                0,
                policy,
                null,
                null,
                clientIp,
                "IN",
                coordinates,
                null,
                null,
                false,
                null,
                null,
                false
        );
    }

    @Test
    @DisplayName("Circle ALLOW rule permits visitor inside perimeter and blocks visitor outside")
    void testCircleAllowRule() throws Exception {
        // Tech Hub in Bangalore ~ [12.9716, 77.5946], Radius 1000m
        GeoFencePolicy policyDto = new GeoFencePolicy(
                "CIRCLE",
                "ALLOW",
                "Bangalore Tech Hub",
                new GeoFencePolicy.CircleParams(12.9716, 77.5946, 1000),
                null
        );
        String json = objectMapper.writeValueAsString(policyDto);

        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setMode(AccessPolicyMode.SECURED);
        accessPolicy.setGeofence(json);

        // 1. Visitor inside (~100m away)
        PolicyContext insideContext = createContext(accessPolicy, new GeoCoordinates(12.9720, 77.5950), "203.0.113.50");
        PolicyEvaluationResult insideResult = rule.evaluate(insideContext);
        assertNull(insideResult, "Visitor inside ALLOW circle boundary should be permitted");

        // 2. Visitor outside (~50km away)
        PolicyContext outsideContext = createContext(accessPolicy, new GeoCoordinates(13.2000, 77.5946), "203.0.113.50");
        PolicyEvaluationResult outsideResult = rule.evaluate(outsideContext);
        assertNotNull(outsideResult, "Visitor outside ALLOW circle boundary should be rejected");
        assertEquals(PolicyEvaluationResult.ViolationType.GEOFENCE_RESTRICTED, outsideResult.violationType());
    }

    @Test
    @DisplayName("Circle BLOCK rule blocks visitor inside perimeter and allows visitor outside")
    void testCircleBlockRule() throws Exception {
        GeoFencePolicy policyDto = new GeoFencePolicy(
                "CIRCLE",
                "BLOCK",
                "Restricted Zone",
                new GeoFencePolicy.CircleParams(28.6139, 77.2090, 500),
                null
        );
        String json = objectMapper.writeValueAsString(policyDto);

        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setMode(AccessPolicyMode.SECURED);
        accessPolicy.setGeofence(json);

        // 1. Visitor inside (~50m away) -> Should be rejected
        PolicyContext insideContext = createContext(accessPolicy, new GeoCoordinates(28.6140, 77.2092), "203.0.113.50");
        PolicyEvaluationResult insideResult = rule.evaluate(insideContext);
        assertNotNull(insideResult, "Visitor inside BLOCK boundary must be rejected");
        assertEquals(PolicyEvaluationResult.ViolationType.GEOFENCE_RESTRICTED, insideResult.violationType());

        // 2. Visitor outside (~10km away) -> Should be allowed
        PolicyContext outsideContext = createContext(accessPolicy, new GeoCoordinates(28.7000, 77.2090), "203.0.113.50");
        PolicyEvaluationResult outsideResult = rule.evaluate(outsideContext);
        assertNull(outsideResult, "Visitor outside BLOCK boundary should be allowed");
    }

    @Test
    @DisplayName("Polygon ALLOW rule checks point inside polygon boundaries")
    void testPolygonAllowRule() throws Exception {
        // Triangle around [10, 10], [10, 20], [20, 10]
        List<GeoFencePolicy.CoordinatePoint> vertices = List.of(
                new GeoFencePolicy.CoordinatePoint(10.0, 10.0),
                new GeoFencePolicy.CoordinatePoint(10.0, 20.0),
                new GeoFencePolicy.CoordinatePoint(20.0, 10.0)
        );
        GeoFencePolicy policyDto = new GeoFencePolicy(
                "POLYGON",
                "ALLOW",
                "Triangle Zone",
                null,
                new GeoFencePolicy.PolygonParams(vertices)
        );
        String json = objectMapper.writeValueAsString(policyDto);

        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setMode(AccessPolicyMode.SECURED);
        accessPolicy.setGeofence(json);

        // Inside point [12, 12]
        PolicyContext insideContext = createContext(accessPolicy, new GeoCoordinates(12.0, 12.0), "203.0.113.50");
        assertNull(rule.evaluate(insideContext));

        // Outside point [25, 25]
        PolicyContext outsideContext = createContext(accessPolicy, new GeoCoordinates(25.0, 25.0), "203.0.113.50");
        PolicyEvaluationResult outsideResult = rule.evaluate(outsideContext);
        assertNotNull(outsideResult);
        assertEquals(PolicyEvaluationResult.ViolationType.GEOFENCE_RESTRICTED, outsideResult.violationType());
    }

    @Test
    @DisplayName("Local loopback dev IPs bypass geofence when coordinates are null")
    void testLoopbackFallback() throws Exception {
        GeoFencePolicy policyDto = new GeoFencePolicy(
                "CIRCLE",
                "ALLOW",
                "Any Zone",
                new GeoFencePolicy.CircleParams(0.0, 0.0, 100),
                null
        );
        String json = objectMapper.writeValueAsString(policyDto);

        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setMode(AccessPolicyMode.SECURED);
        accessPolicy.setGeofence(json);

        // Loopback IP without client coordinates
        PolicyContext devContext = createContext(accessPolicy, null, "127.0.0.1");
        assertNull(rule.evaluate(devContext), "Localhost dev connection should bypass geofence");
    }
}
