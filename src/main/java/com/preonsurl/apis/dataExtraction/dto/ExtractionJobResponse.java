package com.preonsurl.apis.dataExtraction.dto;

import com.preonsurl.apis.dataExtraction.entity.DataExtractionJob;
import com.preonsurl.apis.dataExtraction.enums.ExtractionDataType;
import com.preonsurl.apis.dataExtraction.enums.ExtractionReportType;
import com.preonsurl.apis.dataExtraction.enums.ExtractionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Response object representing an extraction job")
public record ExtractionJobResponse(
        @Schema(description = "Public job identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        String publicId,

        @Schema(description = "Extracted data category", example = "LINK")
        ExtractionDataType dataType,

        @Schema(description = "Exported file format", example = "CSV")
        ExtractionReportType reportType,

        @Schema(description = "Current job processing status", example = "COMPLETED")
        ExtractionStatus status,

        @Schema(description = "Applied start date-time filter", example = "2026-10-01T00:00:00")
        LocalDateTime startDateTime,

        @Schema(description = "Applied end date-time filter", example = "2026-10-05T23:59:59")
        LocalDateTime endDateTime,

        @Schema(description = "Specific link ID if analytics filtered", example = "101")
        Long targetShortUrlId,

        @Schema(description = "Specific link URL if analytics filtered", example = "http://localhost:8081/diwali-sale")
        String targetShortUrl,

        @Schema(description = "Generated file name", example = "links-export-20261005_161530.csv")
        String fileName,

        @Schema(description = "File size in bytes", example = "4096")
        Long fileSizeBytes,

        @Schema(description = "Human-readable file size", example = "4.0 KB")
        String formattedFileSize,

        @Schema(description = "Number of exported records", example = "128")
        Integer recordCount,

        @Schema(description = "Error details if extraction failed", example = "null")
        String errorMessage,

        @Schema(description = "Download URL for the exported file", example = "/api/extract/download/550e8400-e29b-41d4-a716-446655440000")
        String downloadUrl,

        @Schema(description = "Timestamp when extraction was requested", example = "2026-10-05T16:15:30")
        LocalDateTime createdAt,

        @Schema(description = "Timestamp when extraction finished", example = "2026-10-05T16:15:32")
        LocalDateTime completedAt
) {
    public static ExtractionJobResponse fromEntity(DataExtractionJob job) {
        String formattedSize = formatSize(job.getFileSizeBytes());
        String downloadUrl = (job.getStatus() == ExtractionStatus.COMPLETED)
                ? "/api/extract/download/" + job.getPublicId()
                : null;

        String displayUrl = job.getTargetShortUrl();
        if (displayUrl != null && displayUrl.startsWith("IDS:")) {
            String[] ids = displayUrl.substring(4).split(",");
            displayUrl = ids.length + " links selected";
        }

        return new ExtractionJobResponse(
                job.getPublicId(),
                job.getDataType(),
                job.getReportType(),
                job.getStatus(),
                job.getStartDateTime(),
                job.getEndDateTime(),
                job.getTargetShortUrlId(),
                displayUrl,
                job.getFileName(),
                job.getFileSizeBytes(),
                formattedSize,
                job.getRecordCount(),
                job.getErrorMessage(),
                downloadUrl,
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }

    private static String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
