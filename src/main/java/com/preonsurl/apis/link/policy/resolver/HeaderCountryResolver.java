package com.preonsurl.apis.link.policy.resolver;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CountryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Locale;

/**
 * Resolves the client country code from standard edge/CDN/reverse proxy headers.
 */
@Component
public class HeaderCountryResolver implements CountryResolver {
    private final DatabaseReader databaseReader;

    public HeaderCountryResolver(DatabaseReader databaseReader){
        this.databaseReader = databaseReader;

    }

    private static final String[] COUNTRY_HEADERS = {
            "CF-IPCountry",                 // Cloudflare
            "X-Country-Code",               // Standard Reverse Proxies
            "CloudFront-Viewer-Country",    // AWS CloudFront
            "GEOIP-COUNTRY-CODE",           // Nginx GeoIP
            "X-GeoIP-Country",              // HAProxy / Apache GeoIP
            "Fastly-Client-IP-Country"      // Fastly
    };

    @Override
    public String resolveCountry(HttpServletRequest request) {
        String ip = resolveClientIp(request);

        if (ip == null || ip.isBlank()) {
            return null;
        }

        try {
            InetAddress address = InetAddress.getByName(ip);

            CountryResponse response = databaseReader.country(address);
            if (response.country() == null) {
                return null;
            }

            return response.country().isoCode();

        } catch (AddressNotFoundException e) {
            return null;
        } catch (Exception e) {

        }

        if (request == null) {
            return null;
        }

        for (String headerName : COUNTRY_HEADERS) {
            String country = request.getHeader(headerName);
            if (country != null && !country.isBlank()) {
                String normalized = country.trim().toUpperCase(Locale.ROOT);
                if (isValidCountryCode(normalized)) {
                    return normalized;
                }
            }
        }

        return null;
    }

    private boolean isValidCountryCode(String code) {
        if (code == null || code.length() != 2) {
            return false;
        }
        // Exclude unknown/Tor codes if standard
        if ("XX".equals(code) || "T1".equals(code) || "??".equals(code)) {
            return false;
        }
        return code.chars().allMatch(Character::isLetter);
    }

    private String resolveClientIp(HttpServletRequest request) {

        // Cloudflare
        String ip = request.getHeader("CF-Connecting-IP");

        if (isValidIpHeader(ip)) {
            return ip.trim();
        }

        // Standard proxy header
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (isValidIpHeader(forwardedFor)) {
            // X-Forwarded-For can contain:
            // client, proxy1, proxy2
            return forwardedFor.split(",")[0].trim();
        }

        // Nginx
        String realIp = request.getHeader("X-Real-IP");

        if (isValidIpHeader(realIp)) {
            return realIp.trim();
        }

        // Direct connection
        return request.getRemoteAddr();
    }

    private boolean isValidIpHeader(String value) {
        return value != null
                && !value.isBlank()
                && !"unknown".equalsIgnoreCase(value);
    }
}
