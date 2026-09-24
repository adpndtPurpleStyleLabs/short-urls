package com.preonsurl.apis.link.policy.model;

/**
 * Immutable value object representing detected client device and platform characteristics.
 */
public record DeviceInfo(
        DeviceType type,
        DeviceOs os,
        String humanReadable
) {
    public static DeviceInfo unknown() {
        return new DeviceInfo(DeviceType.OTHER, DeviceOs.OTHER, "Unknown Device");
    }

    public boolean isMobile() {
        return type == DeviceType.MOBILE;
    }

    public boolean isTablet() {
        return type == DeviceType.TABLET;
    }

    public boolean isDesktop() {
        return type == DeviceType.DESKTOP;
    }
}
