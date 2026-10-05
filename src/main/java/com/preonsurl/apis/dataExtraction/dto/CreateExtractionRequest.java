package com.preonsurl.apis.dataExtraction.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.preonsurl.apis.dataExtraction.enums.ExtractionDataType;
import com.preonsurl.apis.dataExtraction.enums.ExtractionReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "Request payload to initiate a new data extraction report")
public record CreateExtractionRequest(
        @NotNull(message = "Data type is required (LINK, LINK_ANALYTICS)")
        @Schema(description = "Data type to extract", example = "LINK", allowableValues = {"LINK", "LINK_ANALYTICS"})
        @JsonAlias({"type", "data_type"})
        ExtractionDataType dataType,

        @NotNull(message = "Report type is required (CSV, JSON)")
        @Schema(description = "Report format", example = "CSV", allowableValues = {"CSV", "JSON"})
        @JsonAlias({"format", "report_type"})
        ExtractionReportType reportType,

        @Schema(description = "Start date time filter (inclusive)", example = "2026-10-01T00:00:00")
        @JsonAlias({"start", "startDate", "start_time", "startDateTime", "from"})
        String startDateTime,

        @Schema(description = "End date time filter (inclusive)", example = "2026-10-05T23:59:59")
        @JsonAlias({"end", "endDate", "end_time", "endDateTime", "to"})
        String endDateTime,

        @Schema(description = "Specific Short URL ID (only applicable for LINK_ANALYTICS)", example = "101")
        @JsonAlias({"shortUrlId", "linkId", "urlId", "target_short_url_id"})
        Long shortUrlId,

        @Schema(description = "List of specific Short URL IDs (only applicable for LINK_ANALYTICS)", example = "[101, 102]")
        @JsonAlias({"shortUrlIds", "linkIds", "urlIds", "target_short_url_ids"})
        java.util.List<Long> shortUrlIds,

        @Schema(description = "Specific Short URL string / slug (only applicable for LINK_ANALYTICS)", example = "http://localhost:8081/diwali-sale")
        @JsonAlias({"shortUrl", "url", "target_short_url"})
        String shortUrl
) {
    public CreateExtractionRequest(
            ExtractionDataType dataType,
            ExtractionReportType reportType,
            String startDateTime,
            String endDateTime,
            Long shortUrlId,
            String shortUrl
    ) {
        this(dataType, reportType, startDateTime, endDateTime, shortUrlId, shortUrlId != null ? java.util.List.of(shortUrlId) : null, shortUrl);
    }
}
