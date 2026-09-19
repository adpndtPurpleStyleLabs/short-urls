package com.preonsurl.apis.entity;

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
        name = "tags",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_tags_url_tag",
                        columnNames = {"url_id", "tag"}
                )
        },
        indexes = {
                @Index(name = "idx_tags_url_id", columnList = "url_id"),
                @Index(name = "idx_tags_user_id", columnList = "user_id"),
                @Index(name = "idx_tags_tag", columnList = "tag")
        }
)
public class ShortUrlTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_id", nullable = false)
    private Long urlId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "tag", nullable = false, length = 100)
    private String tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ShortUrlTag(Long urlId, Long userId, String tag) {
        this.urlId = urlId;
        this.userId = userId;
        this.tag = tag;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
