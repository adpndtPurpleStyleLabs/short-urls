package com.preonsurl.apis.analytics.service;

import com.preonsurl.apis.analytics.dto.AnalyticsResponse;
import com.preonsurl.apis.analytics.dto.DailyClickDto;
import com.preonsurl.apis.analytics.dto.UrlAnalyticsResponse;
import com.preonsurl.apis.analytics.repository.AnalyticsRepository;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.exception.UrlNotFoundException;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private final NewUrlRepository newUrlRepository;
    private final String domain;

    public AnalyticsService(
            AnalyticsRepository analyticsRepository,
            NewUrlRepository newUrlRepository,
            @Value("${preonsurl.shortener.domain:}") String domain) {
        this.analyticsRepository = analyticsRepository;
        this.newUrlRepository = newUrlRepository;
        this.domain = (domain != null && !domain.isBlank()) ? domain.trim() : "";
    }

    public AnalyticsResponse getOverallClicks(Long userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null) {
            throw new AccessDeniedException("User ID is required to fetch analytics");
        }

        LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.now();
        LocalDate effectiveStart = (startDate != null) ? startDate : effectiveEnd.minusDays(29);

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }

        LocalDateTime startTime = effectiveStart.atStartOfDay();
        LocalDateTime endTime = effectiveEnd.plusDays(1).atStartOfDay();

        List<Object[]> results = analyticsRepository.getDailyClicksByUser(userId, startTime, endTime);
        List<DailyClickDto> dailyClicks = buildDailyClicksList(effectiveStart, effectiveEnd, results);
        long totalClicks = dailyClicks.stream().mapToLong(DailyClickDto::clicks).sum();

        return new AnalyticsResponse(totalClicks, effectiveStart, effectiveEnd, dailyClicks);
    }

    public UrlAnalyticsResponse getUrlClicks(Long userId, String urlOrShortCode, LocalDate startDate, LocalDate endDate) {
        if (userId == null) {
            throw new AccessDeniedException("User ID is required to fetch analytics");
        }
        if (urlOrShortCode == null || urlOrShortCode.isBlank()) {
            throw new IllegalArgumentException("URL or short code cannot be empty");
        }

        LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.now();
        LocalDate effectiveStart = (startDate != null) ? startDate : effectiveEnd.minusDays(29);

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }

        NewUrl entity = resolveUserUrl(urlOrShortCode.trim(), userId);

        LocalDateTime startTime = effectiveStart.atStartOfDay();
        LocalDateTime endTime = effectiveEnd.plusDays(1).atStartOfDay();

        List<Object[]> results = analyticsRepository.getDailyClicksByShortUrlId(entity.getId(), startTime, endTime);
        List<DailyClickDto> dailyClicks = buildDailyClicksList(effectiveStart, effectiveEnd, results);
        long totalClicks = dailyClicks.stream().mapToLong(DailyClickDto::clicks).sum();

        return new UrlAnalyticsResponse(
                entity.getShortCode(),
                entity.getNewUrl(),
                entity.getOriginalUrl(),
                totalClicks,
                effectiveStart,
                effectiveEnd,
                dailyClicks
        );
    }

    private NewUrl resolveUserUrl(String query, Long userId) {
        // 1. Direct match by newUrl
        Optional<NewUrl> found = newUrlRepository.findByNewUrlAndUserId(query, userId);

        // 2. Prefixed domain match if query does not start with http/https
        if (found.isEmpty() && !query.startsWith("http://") && !query.startsWith("https://")) {
            String prefixed = domain + (query.startsWith("/") ? "" : "/") + query;
            found = newUrlRepository.findByNewUrlAndUserId(prefixed, userId);
        }

        // 3. Match by shortCode
        if (found.isEmpty()) {
            found = newUrlRepository.findByShortCodeAndUserId(query, userId);
        }

        if (found.isPresent()) {
            return found.get();
        }

        // Check if the URL belongs to another user
        boolean existsGlobally = newUrlRepository.findByNewUrl(query).isPresent()
                || newUrlRepository.findByShortCode(query).isPresent();
        if (!existsGlobally && !query.startsWith("http://") && !query.startsWith("https://")) {
            String prefixed = domain + (query.startsWith("/") ? "" : "/") + query;
            existsGlobally = newUrlRepository.findByNewUrl(prefixed).isPresent();
        }

        if (existsGlobally) {
            throw new AccessDeniedException("Access denied to URL analytics");
        }

        throw new UrlNotFoundException("URL not found");
    }

    private List<DailyClickDto> buildDailyClicksList(LocalDate startDate, LocalDate endDate, List<Object[]> queryResults) {
        Map<LocalDate, Long> clickMap = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            clickMap.put(date, 0L);
        }

        if (queryResults != null) {
            for (Object[] row : queryResults) {
                if (row != null && row.length >= 2 && row[0] != null) {
                    LocalDate date = parseLocalDate(row[0]);
                    long count = ((Number) row[1]).longValue();
                    if (clickMap.containsKey(date)) {
                        clickMap.put(date, count);
                    }
                }
            }
        }

        return clickMap.entrySet().stream()
                .map(entry -> new DailyClickDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private LocalDate parseLocalDate(Object obj) {
        if (obj instanceof LocalDate ld) {
            return ld;
        }
        if (obj instanceof java.sql.Date sd) {
            return sd.toLocalDate();
        }
        if (obj instanceof java.util.Date ud) {
            return ud.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (obj != null) {
            String str = obj.toString();
            if (str.length() >= 10) {
                return LocalDate.parse(str.substring(0, 10));
            }
        }
        throw new IllegalArgumentException("Unable to parse date from query result: " + obj);
    }
}
