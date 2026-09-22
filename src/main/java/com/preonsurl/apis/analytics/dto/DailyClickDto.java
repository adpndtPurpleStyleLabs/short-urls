package com.preonsurl.apis.analytics.dto;

import java.time.LocalDate;

public record DailyClickDto(
        LocalDate date,
        long clicks
) {
}
