package com.preonsurl.apis.analytics.dto;

import java.time.LocalDate;

public record DailyCreatedUrlDto(
        LocalDate date,
        long count
) {
}
