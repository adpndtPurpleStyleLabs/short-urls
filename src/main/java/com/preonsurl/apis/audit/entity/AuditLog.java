package com.preonsurl.apis.audit.entity;

import com.preonsurl.apis.audit.enums.AuditAction;
import com.preonsurl.apis.audit.enums.AuditResourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_audit_created", columnList = "created_at"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_resource", columnList = "resource_type")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 64)
    private AuditResourceType resourceType;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "SUCCESS";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AuditLog(Long userId, String username, Long tenantId, AuditAction action,
                    AuditResourceType resourceType, String resourceId, String details,
                    String ipAddress, String userAgent, String status) {
        this.userId = userId;
        this.username = username;
        this.tenantId = tenantId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.status = (status != null && !status.isBlank()) ? status : "SUCCESS";
        this.createdAt = LocalDateTime.now();
    }
}
