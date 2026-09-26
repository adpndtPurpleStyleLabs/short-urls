package com.preonsurl.apis.uptime.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "uptime_table",
        indexes = {
                @Index(name = "idx_uptime_recorded_at", columnList = "recorded_at"),
                @Index(name = "idx_uptime_service_name", columnList = "service_name"),
                @Index(name = "idx_uptime_type", columnList = "type"),
                @Index(name = "idx_uptime_status", columnList = "status")
        }
)
public class UptimeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "type", length = 64)
    private String type;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "details", length = 1024)
    private String details;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    public UptimeRecord(String serviceName, String type, String status, String previousStatus, Long responseTimeMs, Integer statusCode, String details) {
        this.serviceName = serviceName;
        this.type = type;
        this.status = status;
        this.previousStatus = previousStatus;
        this.responseTimeMs = responseTimeMs;
        this.statusCode = statusCode;
        this.details = details;
        this.recordedAt = Instant.now();
    }

    public UptimeRecord(String serviceName, String status, String previousStatus, Long responseTimeMs, Integer statusCode, String details) {
        this(serviceName, serviceName, status, previousStatus, responseTimeMs, statusCode, details);
    }
}
