package com.preonsurl.apis.audit.event;

import com.preonsurl.apis.audit.enums.AuditAction;
import com.preonsurl.apis.audit.enums.AuditResourceType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
public class AuditPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public AuditPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(Long userId, String username, Long tenantId, AuditAction action,
                        AuditResourceType resourceType, String resourceId, String details) {
        publish(userId, username, tenantId, action, resourceType, resourceId, details, "SUCCESS");
    }

    public void publish(Long userId, String username, Long tenantId, AuditAction action,
                        AuditResourceType resourceType, String resourceId, String details, String status) {
        String ipAddress = null;
        String userAgent = null;

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                ipAddress = resolveClientIp(request);
                userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 500) {
                    userAgent = userAgent.substring(0, 500);
                }
            }
        } catch (Exception e) {
            log.trace("Could not resolve servlet request context for audit log: {}", e.getMessage());
        }

        AuditEvent event = new AuditEvent(
                userId,
                username,
                tenantId,
                action,
                resourceType,
                resourceId,
                details,
                ipAddress,
                userAgent,
                status
        );

        eventPublisher.publishEvent(event);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
