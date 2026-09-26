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
        name = "deployments",
        indexes = {
                @Index(name = "idx_deployments_deployed_at", columnList = "deployed_at"),
                @Index(name = "idx_deployments_commit_ref", columnList = "commit_ref")
        }
)
public class DeploymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commit_ref", nullable = false, length = 100)
    private String commitRef;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "environment", length = 50)
    private String environment;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "summary", length = 255)
    private String summary;

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt = Instant.now();

    public DeploymentRecord(String commitRef, String version, String environment, String status, String summary) {
        this.commitRef = commitRef;
        this.version = version;
        this.environment = environment;
        this.status = status;
        this.summary = summary;
        this.deployedAt = Instant.now();
    }
}
