package com.preonsurl.apis.link.controller;

import com.preonsurl.apis.link.dto.ApiResponse;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.enums.LinkMode;
import com.preonsurl.apis.link.event.ShortUrlServedEvent;
import com.preonsurl.apis.link.policy.security.SecurityVerificationService;
import com.preonsurl.apis.link.repository.NewUrlRepository;
import com.preonsurl.apis.link.service.LinkOtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@Tag(name = "Link OTP", description = "Endpoints for requesting and validating link access OTPs")
@RestController
@RequestMapping({"/api/link-otp", "/link-otp"})
public class LinkOtpController {

    private final LinkOtpService linkOtpService;
    private final NewUrlRepository newUrlRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityVerificationService securityVerificationService;
    private final com.preonsurl.apis.link.policy.resolver.GeoCoordinatesResolver geoCoordinatesResolver;

    public LinkOtpController(
            LinkOtpService linkOtpService,
            NewUrlRepository newUrlRepository,
            ApplicationEventPublisher eventPublisher,
            SecurityVerificationService securityVerificationService) {
        this(linkOtpService, newUrlRepository, eventPublisher, securityVerificationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LinkOtpController(
            LinkOtpService linkOtpService,
            NewUrlRepository newUrlRepository,
            ApplicationEventPublisher eventPublisher,
            SecurityVerificationService securityVerificationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.preonsurl.apis.link.policy.resolver.GeoCoordinatesResolver geoCoordinatesResolver) {
        this.linkOtpService = linkOtpService;
        this.newUrlRepository = newUrlRepository;
        this.eventPublisher = eventPublisher;
        this.securityVerificationService = securityVerificationService;
        this.geoCoordinatesResolver = geoCoordinatesResolver;
    }

    public record RequestOtpPayload(String path, String email) {}
    public record VerifyOtpPayload(String path, String email, String otp, Double geo_lat, Double geo_lng) {
        public VerifyOtpPayload(String path, String email, String otp) {
            this(path, email, otp, null, null);
        }
    }

    @Operation(summary = "Request OTP for a secured link", description = "Validates recipient email and dispatches a 6-digit OTP code")
    @PostMapping("/request")
    public ResponseEntity<?> requestOtp(@RequestBody RequestOtpPayload payload, HttpServletRequest request) {
        if (payload == null || payload.path() == null || payload.path().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Link path is required"));
        }

        Optional<NewUrl> entityOpt = findEntityByPath(payload.path().trim(), request);
        if (entityOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Link not found"));
        }

        LinkOtpService.RequestOtpOutcome outcome = linkOtpService.requestOtp(entityOpt.get(), payload.email());
        if (!outcome.success()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(outcome.message()));
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of("message", outcome.message()), outcome.message()));
    }

    @Operation(summary = "Verify OTP for a secured link", description = "Validates submitted 6-digit OTP code and authorizes access")
    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpPayload payload, HttpServletRequest request) {
        if (payload == null || payload.path() == null || payload.path().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Link path is required"));
        }

        Optional<NewUrl> entityOpt = findEntityByPath(payload.path().trim(), request);
        if (entityOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Link not found"));
        }

        NewUrl entity = entityOpt.get();
        LinkOtpService.VerifyOtpOutcome outcome = linkOtpService.verifyOtp(entity, payload.email(), payload.otp());
        if (!outcome.success()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(outcome.message()));
        }

        // Resolve visitor geographic coordinates from payload, edge headers, or cookies
        Double clientLat = payload.geo_lat();
        Double clientLng = payload.geo_lng();
        if ((clientLat == null || clientLng == null) && geoCoordinatesResolver != null) {
            com.preonsurl.apis.link.policy.model.GeoCoordinates coords = geoCoordinatesResolver.resolveCoordinates(request);
            if (coords != null && coords.isValid()) {
                if (clientLat == null) clientLat = coords.latitude();
                if (clientLng == null) clientLng = coords.longitude();
            }
        }
        if (clientLat != null && clientLng != null) {
            if (Math.abs(clientLat) > 90.0 && Math.abs(clientLng) <= 90.0) {
                Double tmp = clientLat;
                clientLat = clientLng;
                clientLng = tmp;
            }
        }

        ResponseCookie geoCoordsCookie = (clientLat != null && clientLng != null)
                ? ResponseCookie.from("PREONS_GEO_COORDS", clientLat + "_" + clientLng)
                    .path("/")
                    .maxAge(3600)
                    .sameSite("Lax")
                    .build()
                : null;

        // Set security cookie using SecurityVerificationService
        ResponseCookie cookie = securityVerificationService.createVerificationCookie(entity.getId());

        String cleanPath = payload.path().trim();
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }

        LinkMode mode = entity.getLinkMode() != null ? entity.getLinkMode() : LinkMode.REDIRECT;
        String targetRedirectUrl;

        if (mode == LinkMode.PROXY || mode == LinkMode.MIRROR) {
            // For PROXY and MIRROR modes, direct visitor to the short link path so ServingController handles proxying or mirroring.
            // Do NOT record ShortUrlServedEvent here; ServingController.resolveAndServe will record it when the page is served.
            targetRedirectUrl = !cleanPath.isBlank() ? ("/" + cleanPath) : entity.getNewUrl();
        } else {
            // For REDIRECT mode, direct to the original target URL.
            targetRedirectUrl = (outcome.redirectUrl() != null && !outcome.redirectUrl().isBlank())
                    ? outcome.redirectUrl()
                    : entity.getOriginalUrl();

            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isBlank()) {
                ipAddress = request.getRemoteAddr();
            } else if (ipAddress.contains(",")) {
                ipAddress = ipAddress.split(",")[0].trim();
            }
            String userAgent = request.getHeader("User-Agent");
            String referer = request.getHeader("Referer");

            eventPublisher.publishEvent(new ShortUrlServedEvent(entity.getId(), entity.getNewUrl(), ipAddress, userAgent, referer, clientLat, clientLng));
        }

        var responseBuilder = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString());
        if (geoCoordsCookie != null) {
            responseBuilder.header(HttpHeaders.SET_COOKIE, geoCoordsCookie.toString());
        }
        return responseBuilder.body(ApiResponse.success(Map.of(
                "redirectUrl", targetRedirectUrl,
                "linkMode", mode.name()
        ), "OTP verified successfully"));
    }

    private Optional<NewUrl> findEntityByPath(String path, HttpServletRequest request) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        Optional<NewUrl> opt = newUrlRepository.findByShortCode(cleanPath);
        if (opt.isPresent()) return opt;

        opt = newUrlRepository.findByCustomPath(cleanPath);
        if (opt.isPresent()) return opt;

        opt = newUrlRepository.findByPublicId(cleanPath);
        if (opt.isPresent()) return opt;

        if (cleanPath.contains("/")) {
            int firstSlash = cleanPath.indexOf('/');
            String prefix = cleanPath.substring(0, firstSlash);
            String code = cleanPath.substring(firstSlash + 1);
            opt = newUrlRepository.findByCustomPathAndShortCode(prefix, code);
            if (opt.isPresent()) return opt;
        }

        String fullUrl = request.getRequestURL().toString();
        opt = newUrlRepository.findByNewUrl(fullUrl);
        return opt;
    }
}
