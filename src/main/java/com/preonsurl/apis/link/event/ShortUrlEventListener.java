package com.preonsurl.apis.link.event;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import com.preonsurl.apis.link.cache.NewUrlLruCache;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.service.CountryCentroids;
import com.preonsurl.apis.link.service.UserAgentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;

@Component
public class ShortUrlEventListener {

    private static final Logger log = LoggerFactory.getLogger(ShortUrlEventListener.class);

    private final NewUrlRepository repository;
    private final NewUrlAccessLogRepository accessLogRepository;
    private final NewUrlLruCache lruCache;
    private final com.preonsurl.apis.link.repository.UsagePolicyRepository usagePolicyRepository;
    private final DatabaseReader databaseReader;

    public ShortUrlEventListener(NewUrlRepository repository,
                                 NewUrlAccessLogRepository accessLogRepository,
                                 NewUrlLruCache lruCache,
                                 com.preonsurl.apis.link.repository.UsagePolicyRepository usagePolicyRepository,
                                 @Autowired(required = false) DatabaseReader databaseReader) {
        this.repository = repository;
        this.accessLogRepository = accessLogRepository;
        this.lruCache = lruCache;
        this.usagePolicyRepository = usagePolicyRepository;
        this.databaseReader = databaseReader;
    }

    @Async
    @EventListener
    @Transactional
    public void onShortUrlServed(ShortUrlServedEvent event) {
        try {
            // 1. Increment click count in DB
            repository.incrementClickCount(event.shortUrlId());

            // 2. Increment usage in usage_policies table
            usagePolicyRepository.incrementUsage(event.shortUrlId());

            // 3. Inspect tracking preferences for this link
            Optional<NewUrl> urlOpt = repository.findById(event.shortUrlId());
            boolean trackLocation = true;
            boolean trackUserAgent = true;
            boolean trackIp = true;
            boolean trackReferrer = true;

            if (urlOpt.isPresent()) {
                NewUrl urlEntity = urlOpt.get();
                trackLocation = urlEntity.isTrackLocationEnabled();
                trackUserAgent = urlEntity.isTrackUserAgentEnabled();
                trackIp = urlEntity.isTrackIpEnabled();
                trackReferrer = urlEntity.isTrackReferrerEnabled();
            }

            // 4. Resolve IP, Referrer, and User Agent telemetry conditionally
            String ipToSave = trackIp ? event.ipAddress() : null;
            String refererToSave = trackReferrer ? event.referer() : null;
            String uaToSave = trackUserAgent ? event.userAgent() : null;

            String device = null;
            String browser = null;
            String os = null;
            if (trackUserAgent && uaToSave != null && !uaToSave.isBlank()) {
                UserAgentParser.UserAgentDetails uaDetails = UserAgentParser.parse(uaToSave);
                device = uaDetails.device();
                browser = uaDetails.browser();
                os = uaDetails.os();
            }

            // 5. Resolve Geographic Location telemetry conditionally
            String country = null;
            String city = null;
            Double latitude = null;
            Double longitude = null;

            if (trackLocation) {
                // If coordinates were explicitly provided in event (e.g. from browser)
                if (event.latitude() != null && event.longitude() != null) {
                    double lat = event.latitude();
                    double lng = event.longitude();
                    if (Math.abs(lat) > 90.0 && Math.abs(lng) <= 90.0) {
                        double tmp = lat;
                        lat = lng;
                        lng = tmp;
                    }
                    if (Math.abs(lat) <= 90.0 && Math.abs(lng) <= 180.0) {
                        latitude = lat;
                        longitude = lng;
                    }
                }

                // Resolve country name & centroid coordinates from IP if needed
                String countryCode = null;
                if (databaseReader != null && event.ipAddress() != null && !event.ipAddress().isBlank()) {
                    try {
                        String cleanIp = event.ipAddress().trim();
                        if (cleanIp.contains(",")) {
                            cleanIp = cleanIp.split(",")[0].trim();
                        }
                        InetAddress addr = InetAddress.getByName(cleanIp);
                        CountryResponse resp = databaseReader.country(addr);
                        if (resp != null && resp.country() != null) {
                            countryCode = resp.country().isoCode();
                            country = resp.country().name();
                        }
                    } catch (Exception ignored) {}
                }

                if (countryCode != null) {
                    CountryCentroids.Coordinates centroid = CountryCentroids.get(countryCode);
                    if (centroid != null) {
                        if (country == null) {
                            country = centroid.countryName();
                        }
                        if (latitude == null || longitude == null) {
                            latitude = centroid.latitude();
                            longitude = centroid.longitude();
                        }
                    }
                }

                // If local / private IP and no coords resolved yet, provide default fallback coordinates
                if (latitude == null && longitude == null) {
                    String ip = event.ipAddress();
                    if (ip != null && (ip.startsWith("127.") || ip.startsWith("192.168.") || ip.startsWith("10.") || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equalsIgnoreCase(ip))) {
                        country = "Local / Development";
                        latitude = 20.5937;
                        longitude = 78.9629;
                    }
                }
            }

            // 6. Save access log with populated telemetry
            NewUrlAccessLog accessLog = new NewUrlAccessLog(
                    event.shortUrlId(),
                    event.newUrl(),
                    ipToSave,
                    uaToSave,
                    refererToSave,
                    country,
                    city,
                    latitude,
                    longitude,
                    device,
                    browser,
                    os
            );
            if (trackLocation && event.accuracy() != null) {
                accessLog.setAccuracy(event.accuracy());
            }
            accessLogRepository.save(accessLog);

            // 7. Check DB state to verify expiration, usage limit, and active status
            if (urlOpt.isPresent()) {
                NewUrl entity = urlOpt.get();
                boolean expired = entity.getExpireAt() != null && Instant.now().isAfter(entity.getExpireAt());
                boolean limitReached = entity.getUsageLimit() != null && entity.getClickCount() >= entity.getUsageLimit();
                boolean inactive = !entity.isActive();

                // Also check UsagePolicy if limit reached or expired
                Optional<com.preonsurl.apis.link.entity.UsagePolicy> upOpt = usagePolicyRepository.findByShortUrlId(event.shortUrlId());
                boolean policyExhausted = upOpt.map(com.preonsurl.apis.link.entity.UsagePolicy::isExhausted).orElse(false);

                if (expired || limitReached || inactive || policyExhausted) {
                    log.info("Removing breached/expired/inactive short code '{}' from LRU cache [expired={}, limitReached={}, inactive={}, policyExhausted={}]",
                            event.newUrl(), expired, limitReached, inactive, policyExhausted);
                    lruCache.remove(event.newUrl());
                }
            }

        } catch (Exception e) {
            log.error("Failed to process ShortUrlServedEvent for full url: {}", event.newUrl(), e);
        }
    }
}

