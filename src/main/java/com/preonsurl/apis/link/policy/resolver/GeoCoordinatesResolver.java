package com.preonsurl.apis.link.policy.resolver;

import com.preonsurl.apis.link.policy.model.GeoCoordinates;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface for resolving geographic coordinates from client request or edge headers.
 */
public interface GeoCoordinatesResolver {
    GeoCoordinates resolveCoordinates(HttpServletRequest request);
}
