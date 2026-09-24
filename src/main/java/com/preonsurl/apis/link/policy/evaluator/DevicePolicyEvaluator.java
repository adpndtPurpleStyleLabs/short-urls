package com.preonsurl.apis.link.policy.evaluator;

import com.preonsurl.apis.link.policy.model.DeviceInfo;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Evaluates whether a client's device type or operating system satisfies device restrictions.
 */
@Component
public class DevicePolicyEvaluator {

    /**
     * Determines whether the detected device is authorized.
     *
     * @param device The detected DeviceInfo
     * @param allowedDeviceTypesStr Comma-separated list of allowed devices/platforms (e.g. "MOBILE, TABLET" or "IOS, ANDROID")
     * @return true if authorized, false if incompatible
     */
    public boolean isAllowed(DeviceInfo device, String allowedDeviceTypesStr) {
        if (allowedDeviceTypesStr == null || allowedDeviceTypesStr.isBlank()) {
            return true; // No restriction configured
        }

        if (device == null) {
            return false;
        }

        String[] allowed = allowedDeviceTypesStr.split(",");
        String clientTypeName = device.type().name();
        String clientOsName = device.os().name();

        for (String raw : allowed) {
            String token = raw.trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }

            // Check if matches device type (MOBILE, TABLET, DESKTOP)
            if (token.equals(clientTypeName)) {
                return true;
            }

            // Check if matches OS (IOS, ANDROID, WINDOWS, MACOS, LINUX)
            if (token.equals(clientOsName)) {
                return true;
            }
        }

        return false;
    }
}
