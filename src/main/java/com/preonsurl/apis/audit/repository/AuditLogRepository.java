package com.preonsurl.apis.audit.repository;

import com.preonsurl.apis.audit.entity.AuditLog;
import com.preonsurl.apis.audit.enums.AuditAction;
import com.preonsurl.apis.audit.enums.AuditResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:action IS NULL OR a.action = :action)
              AND (:resourceType IS NULL OR a.resourceType = :resourceType)
              AND (:startDate IS NULL OR a.createdAt >= :startDate)
              AND (:endDate IS NULL OR a.createdAt <= :endDate)
            """)
    Page<AuditLog> findFiltered(
            @Param("userId") Long userId,
            @Param("action") AuditAction action,
            @Param("resourceType") AuditResourceType resourceType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:action IS NULL OR a.action = :action)
              AND (:resourceType IS NULL OR a.resourceType = :resourceType)
              AND (:startDate IS NULL OR a.createdAt >= :startDate)
              AND (:endDate IS NULL OR a.createdAt <= :endDate)
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findAllFiltered(
            @Param("userId") Long userId,
            @Param("action") AuditAction action,
            @Param("resourceType") AuditResourceType resourceType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
