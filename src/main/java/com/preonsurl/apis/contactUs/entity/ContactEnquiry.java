package com.preonsurl.apis.contactUs.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "contact_enquiries",
        indexes = {
                @Index(name = "idx_contact_email", columnList = "email"),
                @Index(name = "idx_contact_topic", columnList = "topic"),
                @Index(name = "idx_contact_created_at", columnList = "created_at")
        }
)
public class ContactEnquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "company", length = 150)
    private String company;

    @Column(name = "topic", nullable = false, length = 30)
    private String topic;

    @Column(name = "message", nullable = false, length = 5000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContactEnquiry() {
    }

    public ContactEnquiry(
            String fullName,
            String email,
            String company,
            String topic,
            String message
    ) {
        this.fullName = fullName;
        this.email = email;
        this.company = company;
        this.topic = topic;
        this.message = message;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getCompany() {
        return company;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}