package com.preonsurl.apis.dataExtraction.entity;

import com.preonsurl.apis.dataExtraction.enums.ExtractionDataType;
import com.preonsurl.apis.dataExtraction.enums.ExtractionReportType;
import com.preonsurl.apis.dataExtraction.enums.ExtractionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_extraction_jobs", indexes = {
        @Index(name = "idx_dej_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_dej_public_id", columnList = "public_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DataExtractionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 32)
    private ExtractionDataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 16)
    private ExtractionReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExtractionStatus status = ExtractionStatus.PROCESSING;

    @Column(name = "start_date_time")
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time")
    private LocalDateTime endDateTime;

    @Column(name = "target_short_url_id")
    private Long targetShortUrlId;

    @Column(name = "target_short_url", length = 512)
    private String targetShortUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", length = 512)
    private String filePath;

    @Lob
    @Column(name = "file_content", columnDefinition = "LONGTEXT")
    private String fileContent;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes = 0L;

    @Column(name = "record_count")
    private Integer recordCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public DataExtractionJob(String publicId, Long userId, ExtractionDataType dataType, ExtractionReportType reportType) {
        this.publicId = publicId;
        this.userId = userId;
        this.dataType = dataType;
        this.reportType = reportType;
        this.status = ExtractionStatus.PROCESSING;
        this.createdAt = LocalDateTime.now();
    }
}
