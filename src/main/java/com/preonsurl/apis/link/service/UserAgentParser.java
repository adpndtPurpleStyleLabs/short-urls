package com.preonsurl.apis.link.service;

import java.util.Locale;

public final class UserAgentParser {

    public record UserAgentDetails(String device, String browser, String os) {}

    private UserAgentParser() {}

    public static UserAgentDetails parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new UserAgentDetails("Unknown", "Unknown", "Unknown");
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);

        // 1. Device Category
        String device = "Desktop";
        if (ua.contains("mobile") || ua.contains("iphone") || (ua.contains("android") && !ua.contains("tablet"))) {
            device = "Mobile";
        } else if (ua.contains("ipad") || ua.contains("tablet") || (ua.contains("android") && ua.contains("tablet"))) {
            device = "Tablet";
        } else if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider") || ua.contains("curl") || ua.contains("postman")) {
            device = "Bot";
        }

        // 2. Browser
        String browser = "Other";
        if (ua.contains("edg/") || ua.contains("edge/")) {
            browser = "Edge";
        } else if (ua.contains("opr/") || ua.contains("opera/")) {
            browser = "Opera";
        } else if (ua.contains("samsungbrowser")) {
            browser = "Samsung Internet";
        } else if (ua.contains("chrome/") || ua.contains("crios/")) {
            browser = "Chrome";
        } else if (ua.contains("firefox/") || ua.contains("fxios/")) {
            browser = "Firefox";
        } else if (ua.contains("safari/") && !ua.contains("chrome") && !ua.contains("android")) {
            browser = "Safari";
        } else if (ua.contains("msie") || ua.contains("trident/")) {
            browser = "Internet Explorer";
        }

        // 3. Operating System
        String os = "Other";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            os = "iOS";
        } else if (ua.contains("android")) {
            os = "Android";
        } else if (ua.contains("macintosh") || ua.contains("mac os")) {
            os = "macOS";
        } else if (ua.contains("windows")) {
            os = "Windows";
        } else if (ua.contains("cros")) {
            os = "ChromeOS";
        } else if (ua.contains("linux")) {
            os = "Linux";
        }

        return new UserAgentDetails(device, browser, os);
    }
}
