package com.preonsurl.apis.audit.listener;

import com.preonsurl.apis.audit.entity.AuditLog;
import com.preonsurl.apis.audit.event.AuditEvent;
import com.preonsurl.apis.audit.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    public AuditEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        try {
            AuditLog logEntity = new AuditLog(
                    event.userId(),
                    event.username(),
                    event.tenantId(),
                    event.action(),
                    event.resourceType(),
                    event.resourceId(),
                    event.details(),
                    event.ipAddress(),
                    event.userAgent(),
                    event.status()
            );
            auditLogRepository.save(logEntity);
            log.debug("Recorded audit log: action={}, user={}, resource={}",
                    event.action(), event.username() != null ? event.username() : event.userId(), event.resourceId());
        } catch (Exception e) {
            log.error("Failed to asynchronously persist audit log for action={}: {}", event.action(), e.getMessage(), e);
        }
    }
}
