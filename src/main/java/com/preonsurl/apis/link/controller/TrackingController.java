package com.preonsurl.apis.link.controller;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import com.preonsurl.apis.link.entity.LinkRecipient;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.NewUrlAccessLog;
import com.preonsurl.apis.link.policy.resolver.ClientIpResolver;
import com.preonsurl.apis.link.policy.resolver.CountryResolver;
import com.preonsurl.apis.link.repository.LinkRecipientRepository;
import com.preonsurl.apis.link.repository.NewUrlAccessLogRepository;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.service.CountryCentroids;
import com.preonsurl.apis.link.service.UserAgentParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;

@Tag(name = "Tracking", description = "Email open tracking beacon endpoint")
@RestController
@RequestMapping({"/api/track", "/track"})
public class TrackingController {

    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);

    // 1x1 transparent PNG binary bytes
    private static final byte[] TRANSPARENT_1PX_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, (byte) 0xc4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0a, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9c, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, (byte) 0xb4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, (byte) 0xae,
            0x42, 0x60, (byte) 0x82
    };

    private final LinkRecipientRepository linkRecipientRepository;
    private final NewUrlRepository newUrlRepository;
    private final NewUrlAccessLogRepository accessLogRepository;
    private final ClientIpResolver clientIpResolver;
    private final CountryResolver countryResolver;
    private final DatabaseReader databaseReader;
    private final com.preonsurl.apis.link.service.ReverseGeocodingService reverseGeocodingService;

    @Autowired
    public TrackingController(
            LinkRecipientRepository linkRecipientRepository,
            NewUrlRepository newUrlRepository,
            NewUrlAccessLogRepository accessLogRepository,
            ClientIpResolver clientIpResolver,
            CountryResolver countryResolver,
            @Autowired(required = false) DatabaseReader databaseReader,
            @Autowired(required = false) com.preonsurl.apis.link.service.ReverseGeocodingService reverseGeocodingService
    ) {
        this.linkRecipientRepository = linkRecipientRepository;
        this.newUrlRepository = newUrlRepository;
        this.accessLogRepository = accessLogRepository;
        this.clientIpResolver = clientIpResolver;
        this.countryResolver = countryResolver;
        this.databaseReader = databaseReader;
        this.reverseGeocodingService = reverseGeocodingService;
    }

    public TrackingController(
            LinkRecipientRepository linkRecipientRepository,
            NewUrlRepository newUrlRepository,
            NewUrlAccessLogRepository accessLogRepository,
            ClientIpResolver clientIpResolver,
            CountryResolver countryResolver,
            DatabaseReader databaseReader
    ) {
        this(linkRecipientRepository, newUrlRepository, accessLogRepository, clientIpResolver, countryResolver, databaseReader, null);
    }

    public TrackingController(LinkRecipientRepository linkRecipientRepository) {
        this(linkRecipientRepository, null, null, null, null, null, null);
    }

    public ResponseEntity<byte[]> trackEmailOpen(String token) {
        return trackEmailOpen(token, null);
    }

    @Operation(summary = "Track email open beacon", description = "Records email open event, client IP and geographic location, and returns 1px transparent PNG")
    @GetMapping(value = {"/email-open/{token}", "/pixel/{token}"}, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> trackEmailOpen(
            @PathVariable("token") String token,
            HttpServletRequest request
    ) {
        try {
            if (token != null && !token.isBlank()) {
                String cleanToken = token.trim();

                // 1. Resolve client IP address
                String clientIp = null;
                if (clientIpResolver != null && request != null) {
                    clientIp = clientIpResolver.resolveClientIp(request);
                } else if (request != null) {
                    clientIp = request.getRemoteAddr();
                }
                if (clientIp == null || clientIp.isBlank()) {
                    clientIp = "127.0.0.1";
                }

                // 2. Resolve geographic location & centroid from IP
                String country = null;
                String countryCode = null;
                String city = null;
                Double latitude = null;
                Double longitude = null;

                if (databaseReader != null) {
                    try {
                        String ipToLookup = clientIp.contains(",") ? clientIp.split(",")[0].trim() : clientIp.trim();
                        InetAddress addr = InetAddress.getByName(ipToLookup);
                        CountryResponse resp = databaseReader.country(addr);
                        if (resp != null && resp.country() != null) {
                            countryCode = resp.country().isoCode();
                            country = resp.country().name();
                        }
                    } catch (Exception ignored) {}
                }

                if (countryCode == null && countryResolver != null && request != null) {
                    countryCode = countryResolver.resolveCountry(request);
                    if (country == null) {
                        country = countryCode;
                    }
                }

                if (countryCode != null) {
                    CountryCentroids.Coordinates centroid = CountryCentroids.get(countryCode);
                    if (centroid != null) {
                        if (country == null) {
                            country = centroid.countryName();
                        }
                        latitude = centroid.latitude();
                        longitude = centroid.longitude();
                    }
                }

                if (latitude == null && longitude == null) {
                    if (clientIp.startsWith("127.") || clientIp.startsWith("192.168.") || clientIp.startsWith("10.") || "0:0:0:0:0:0:0:1".equals(clientIp) || "localhost".equalsIgnoreCase(clientIp)) {
                        country = "Local / Development";
                        latitude = 20.5937;
                        longitude = 78.9629;
                    }
                }

                // 3. User-Agent parsing
                String userAgent = request != null ? request.getHeader("User-Agent") : null;
                String device = null;
                String browser = null;
                String os = null;
                if (userAgent != null && !userAgent.isBlank()) {
                    UserAgentParser.UserAgentDetails details = UserAgentParser.parse(userAgent);
                    device = details.device();
                    browser = details.browser();
                    os = details.os();
                }

                // 4. Record open tracking
                // Case A: Token matches a LinkRecipient tracking token
                Optional<LinkRecipient> recipientOpt = linkRecipientRepository.findByTrackingToken(cleanToken);
                if (recipientOpt.isPresent()) {
                    LinkRecipient recipient = recipientOpt.get();
                    if (!recipient.isEmailOpened()) {
                        recipient.setEmailOpened(true);
                        recipient.setEmailOpenedAt(Instant.now());
                        recipient.setOpenedIp(clientIp);
                        recipient.setOpenedCountry(country);
                        recipient.setOpenedCity(city);
                        recipient.setOpenedUserAgent(userAgent);
                        linkRecipientRepository.save(recipient);
                        log.info("Email open tracked for recipient '{}' (shortUrlId={}) from IP='{}' ({}, {})",
                                recipient.getEmail(), recipient.getShortUrlId(), clientIp, country, city);
                    }

                    // Save access log telemetry
                    if (accessLogRepository != null && newUrlRepository != null) {
                        Optional<NewUrl> urlOpt = newUrlRepository.findById(recipient.getShortUrlId());
                        if (urlOpt.isPresent()) {
                            NewUrl url = urlOpt.get();
                            saveEmailOpenAccessLog(url, clientIp, userAgent, country, city, latitude, longitude, device, browser, os);
                        }
                    }
                } else if (newUrlRepository != null && accessLogRepository != null) {
                    // Case B: Token matches a short link (publicId or shortCode, e.g. shared via modal)
                    String lookupKey = cleanToken.startsWith("email_") ? cleanToken.substring(6) : cleanToken;
                    Optional<NewUrl> urlOpt = newUrlRepository.findByPublicId(lookupKey);
                    if (urlOpt.isEmpty()) {
                        urlOpt = newUrlRepository.findByShortCode(lookupKey);
                    }
                    if (urlOpt.isPresent()) {
                        NewUrl url = urlOpt.get();
                        saveEmailOpenAccessLog(url, clientIp, userAgent, country, city, latitude, longitude, device, browser, os);
                        log.info("Email open tracked for short link '{}' (id={}) from IP='{}' ({}, {})",
                                url.getShortCode(), url.getId(), clientIp, country, city);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to process email tracking token '{}': {}", token, e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setCacheControl("no-cache, no-store, must-revalidate, max-age=0");
        headers.setPragma("no-cache");
        headers.setExpires(0L);

        return ResponseEntity.ok()
                .headers(headers)
                .body(TRANSPARENT_1PX_PNG);
    }

    private void saveEmailOpenAccessLog(NewUrl url, String clientIp, String userAgent,
                                        String country, String city, Double latitude, Double longitude,
                                        String device, String browser, String os) {
        try {
            String region = null;
            if (latitude != null && longitude != null && reverseGeocodingService != null) {
                try {
                    var geo = reverseGeocodingService.reverseGeocode(latitude, longitude);
                    if (geo != null) {
                        if ((city == null || city.isBlank()) && geo.city() != null && !geo.city().isBlank()) {
                            city = geo.city();
                        }
                        if ((country == null || country.isBlank()) && geo.country() != null && !geo.country().isBlank()) {
                            country = geo.country();
                        }
                        if (geo.region() != null && !geo.region().isBlank()) {
                            region = geo.region();
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (region == null || region.isBlank()) {
                if (reverseGeocodingService != null) {
                    region = reverseGeocodingService.formatRegion(city, null, country);
                } else if (city != null && !city.isBlank() && country != null && !country.isBlank()) {
                    region = city + ", " + country;
                } else if (country != null && !country.isBlank()) {
                    region = country;
                }
            }

            NewUrlAccessLog accessLog = new NewUrlAccessLog(
                    url.getId(),
                    url.getShortCode(),
                    clientIp,
                    userAgent,
                    "Email Open Beacon (1px Tracking Pixel)",
                    country,
                    city,
                    region,
                    latitude,
                    longitude,
                    device,
                    browser,
                    os
            );
            accessLogRepository.save(accessLog);
        } catch (Exception e) {
            log.warn("Failed to save email open access log for shortUrlId {}: {}", url.getId(), e.getMessage());
        }
    }
}
