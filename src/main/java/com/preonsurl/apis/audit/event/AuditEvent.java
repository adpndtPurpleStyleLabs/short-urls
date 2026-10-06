package com.preonsurl.apis.audit.event;

import com.preonsurl.apis.audit.enums.AuditAction;
import com.preonsurl.apis.audit.enums.AuditResourceType;

public record AuditEvent(
        Long userId,
        String username,
        Long tenantId,
        AuditAction action,
        AuditResourceType resourceType,
        String resourceId,
        String details,
        String ipAddress,
        String userAgent,
        String status
) {
    public AuditEvent(Long userId, String username, Long tenantId, AuditAction action,
                      AuditResourceType resourceType, String resourceId, String details) {
        this(userId, username, tenantId, action, resourceType, resourceId, details, null, null, "SUCCESS");
    }

    public AuditEvent withClientInfo(String ipAddress, String userAgent) {
        return new AuditEvent(
                this.userId,
                this.username,
                this.tenantId,
                this.action,
                this.resourceType,
                this.resourceId,
                this.details,
                ipAddress,
                userAgent,
                this.status
        );
    }
}
