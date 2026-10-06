package com.preonsurl.apis.audit.dto;

import com.preonsurl.apis.audit.entity.AuditLog;
import com.preonsurl.apis.audit.enums.AuditAction;
import com.preonsurl.apis.audit.enums.AuditResourceType;
import com.preonsurl.apis.link.service.UserAgentParser;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long userId,
        String username,
        AuditAction action,
        AuditResourceType resourceType,
        String resourceId,
        String details,
        String ipAddress,
        String userAgent,
        String status,
        LocalDateTime createdAt,
        LocalDateTime timestamp,
        String device,
        String browser,
        String os
) {
    public static AuditLogResponse fromEntity(AuditLog entity) {
        if (entity == null) return null;
        UserAgentParser.UserAgentDetails ua = UserAgentParser.parse(entity.getUserAgent());
        return new AuditLogResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getDetails(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getCreatedAt(),
                ua.device(),
                ua.browser(),
                ua.os()
        );
    }
}
