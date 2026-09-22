package com.preonsurl.apis.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsResponse(
        long totalClicks,
        LocalDate startDate,
        LocalDate endDate,
        List<DailyClickDto> dailyClicks,
        long totalUrls,
        long totalActiveUrls,
        List<DailyCreatedUrlDto> dailyCreatedUrls
) {
    public AnalyticsResponse(long totalClicks, LocalDate startDate, LocalDate endDate, List<DailyClickDto> dailyClicks) {
        this(totalClicks, startDate, endDate, dailyClicks, 0L, 0L, List.of());
    }
}
