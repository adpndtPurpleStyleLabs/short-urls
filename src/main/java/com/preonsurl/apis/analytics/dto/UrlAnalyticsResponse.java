package com.preonsurl.apis.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record UrlAnalyticsResponse(
        String shortCode,
        String newUrl,
        String originalUrl,
        long totalClicks,
        LocalDate startDate,
        LocalDate endDate,
        List<DailyClickDto> dailyClicks
) {
}
