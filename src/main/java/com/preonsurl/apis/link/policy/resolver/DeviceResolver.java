package com.preonsurl.apis.link.policy.resolver;

import com.preonsurl.apis.link.policy.model.DeviceInfo;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface for classifying client device type and operating system.
 */
public interface DeviceResolver {
    DeviceInfo resolveDevice(HttpServletRequest request);
}
