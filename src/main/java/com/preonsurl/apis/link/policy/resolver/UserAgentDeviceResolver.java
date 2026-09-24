package com.preonsurl.apis.link.policy.resolver;

import com.preonsurl.apis.link.policy.model.DeviceInfo;
import com.preonsurl.apis.link.policy.model.DeviceOs;
import com.preonsurl.apis.link.policy.model.DeviceType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Classifies client device and operating system based on User-Agent and Sec-CH-UA-Mobile headers.
 */
@Component
public class UserAgentDeviceResolver implements DeviceResolver {

    @Override
    public DeviceInfo resolveDevice(HttpServletRequest request) {
        if (request == null) {
            return DeviceInfo.unknown();
        }

        String userAgent = request.getHeader("User-Agent");
        String chMobile = request.getHeader("Sec-CH-UA-Mobile");

        if (userAgent == null || userAgent.isBlank()) {
            if ("?1".equals(chMobile)) {
                return new DeviceInfo(DeviceType.MOBILE, DeviceOs.OTHER, "Mobile Device");
            }
            return DeviceInfo.unknown();
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);

        // 1. Detect OS
        DeviceOs os = DeviceOs.OTHER;
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            os = DeviceOs.IOS;
        } else if (ua.contains("android")) {
            os = DeviceOs.ANDROID;
        } else if (ua.contains("windows nt") || ua.contains("win64") || ua.contains("win32")) {
            os = DeviceOs.WINDOWS;
        } else if (ua.contains("macintosh") || ua.contains("mac os x")) {
            os = DeviceOs.MACOS;
        } else if (ua.contains("linux") && !ua.contains("android")) {
            os = DeviceOs.LINUX;
        }

        // 2. Detect Device Type
        DeviceType type;
        if (ua.contains("ipad") || (ua.contains("android") && !ua.contains("mobile")) || ua.contains("tablet")) {
            type = DeviceType.TABLET;
        } else if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("ipod") || "?1".equals(chMobile)) {
            type = DeviceType.MOBILE;
        } else if (os == DeviceOs.WINDOWS || os == DeviceOs.MACOS || os == DeviceOs.LINUX) {
            type = DeviceType.DESKTOP;
        } else {
            type = DeviceType.OTHER;
        }

        // 3. Human readable representation
        String human = buildHumanReadable(type, os);
        return new DeviceInfo(type, os, human);
    }

    private String buildHumanReadable(DeviceType type, DeviceOs os) {
        String typeStr = switch (type) {
            case MOBILE -> "Mobile";
            case TABLET -> "Tablet";
            case DESKTOP -> "Desktop";
            case OTHER -> "Device";
        };

        String osStr = switch (os) {
            case IOS -> "iOS";
            case ANDROID -> "Android";
            case WINDOWS -> "Windows";
            case MACOS -> "macOS";
            case LINUX -> "Linux";
            case OTHER -> null;
        };

        if (osStr != null) {
            return typeStr + " (" + osStr + ")";
        }
        return typeStr;
    }
}
