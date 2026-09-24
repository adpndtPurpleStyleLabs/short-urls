package com.preonsurl.apis.link.policy.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Byte-level IP and CIDR matching utility supporting both IPv4 and IPv6 addresses.
 */
@Component
public class CidrMatcher {

    private static final Logger log = LoggerFactory.getLogger(CidrMatcher.class);

    /**
     * Checks if a client IP matches an exact IP or CIDR range.
     *
     * @param clientIpStr The client IP to test
     * @param targetCidrOrIp The target IP or CIDR block (e.g., "192.168.1.0/24" or "10.0.0.1")
     * @return true if the client IP matches the target rule
     */
    public boolean matches(String clientIpStr, String targetCidrOrIp) {
        if (clientIpStr == null || targetCidrOrIp == null) {
            return false;
        }

        String clientIp = cleanIp(clientIpStr);
        String target = targetCidrOrIp.trim();

        if (clientIp.isEmpty() || target.isEmpty()) {
            return false;
        }

        // Direct string match first
        if (clientIp.equalsIgnoreCase(target)) {
            return true;
        }

        // Normalize loopback equivalents
        if (isLoopback(clientIp) && isLoopback(target)) {
            return true;
        }

        try {
            InetAddress clientAddr = InetAddress.getByName(clientIp);

            if (target.contains("/")) {
                String[] parts = target.split("/");
                if (parts.length != 2) {
                    return false;
                }
                InetAddress networkAddr = InetAddress.getByName(parts[0].trim());
                int prefixLength = Integer.parseInt(parts[1].trim());

                return isInSubnet(clientAddr, networkAddr, prefixLength);
            } else {
                InetAddress targetAddr = InetAddress.getByName(target);
                return clientAddr.equals(targetAddr);
            }
        } catch (UnknownHostException | NumberFormatException e) {
            log.debug("Failed to parse IP or CIDR: client='{}', target='{}'", clientIp, target);
            return false;
        }
    }

    public String cleanIp(String rawIp) {
        if (rawIp == null) {
            return "";
        }
        String ip = rawIp.trim();
        // Remove brackets if IPv6 with port like [2001:db8::1]:8080
        if (ip.startsWith("[") && ip.contains("]")) {
            int closingBracket = ip.indexOf(']');
            return ip.substring(1, closingBracket);
        }
        // If IPv4 with port like 192.168.1.1:8080
        if (ip.contains(":") && ip.indexOf(':') == ip.lastIndexOf(':')) {
            return ip.substring(0, ip.indexOf(':'));
        }
        return ip;
    }

    public boolean isLoopback(String ip) {
        if (ip == null) return false;
        String cleaned = cleanIp(ip);
        return "127.0.0.1".equals(cleaned) || "::1".equals(cleaned) || "0:0:0:0:0:0:0:1".equalsIgnoreCase(cleaned) || "localhost".equalsIgnoreCase(cleaned);
    }

    private boolean isInSubnet(InetAddress clientAddr, InetAddress networkAddr, int prefixLength) {
        byte[] clientBytes = clientAddr.getAddress();
        byte[] networkBytes = networkAddr.getAddress();

        if (clientBytes.length != networkBytes.length) {
            return false;
        }

        int maxPrefix = clientBytes.length * 8;
        if (prefixLength < 0 || prefixLength > maxPrefix) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (clientBytes[i] != networkBytes[i]) {
                return false;
            }
        }

        int remainingBits = prefixLength % 8;
        if (remainingBits > 0) {
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (clientBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        }

        return true;
    }
}
