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
        name = "incident_table",
        indexes = {
                @Index(name = "idx_incident_created_at", columnList = "created_at"),
                @Index(name = "idx_incident_status", columnList = "status")
        }
)
public class IncidentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "RESOLVED"; // RESOLVED, INVESTIGATING, IDENTIFIED, MONITORING, OPERATIONAL

    @Column(name = "severity", length = 50)
    private String severity = "MINOR"; // MINOR, MAJOR, CRITICAL, MAINTENANCE

    @Column(name = "impacted_service", length = 100)
    private String impactedService = "Global";

    @Column(name = "incident_date")
    private Instant incidentDate;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public IncidentRecord(String title, String description, String status, String severity, String impactedService, Instant incidentDate, Instant resolvedAt) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.severity = severity;
        this.impactedService = impactedService;
        this.incidentDate = incidentDate != null ? incidentDate : Instant.now();
        this.resolvedAt = resolvedAt;
        this.createdAt = Instant.now();
    }
}
