package com.preonsurl.apis.link.policy;

import com.preonsurl.apis.link.entity.AccessPolicy;
import com.preonsurl.apis.link.entity.NewUrl;
import com.preonsurl.apis.link.entity.UsagePolicy;
import com.preonsurl.apis.link.policy.model.DeviceInfo;
import com.preonsurl.apis.link.policy.resolver.ClientIpResolver;
import com.preonsurl.apis.link.policy.resolver.CountryResolver;
import com.preonsurl.apis.link.policy.resolver.DeviceResolver;
import com.preonsurl.apis.link.policy.security.SecurityVerificationService;
import com.preonsurl.apis.link.repository.AccessPolicyRepository;
import com.preonsurl.apis.link.repository.UsagePolicyRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Factory responsible for assembling the immutable PolicyContext from database entities,
 * environmental resolvers, and HTTP servlet requests once per evaluation cycle.
 */
@Component
public class PolicyContextFactory {

    private final AccessPolicyRepository accessPolicyRepository;
    private final UsagePolicyRepository usagePolicyRepository;
    private final ClientIpResolver clientIpResolver;
    private final CountryResolver countryResolver;
    private final DeviceResolver deviceResolver;
    private final SecurityVerificationService securityVerificationService;

    public PolicyContextFactory(
            AccessPolicyRepository accessPolicyRepository,
            UsagePolicyRepository usagePolicyRepository,
            ClientIpResolver clientIpResolver,
            CountryResolver countryResolver,
            DeviceResolver deviceResolver,
            SecurityVerificationService securityVerificationService
    ) {
        this.accessPolicyRepository = accessPolicyRepository;
        this.usagePolicyRepository = usagePolicyRepository;
        this.clientIpResolver = clientIpResolver;
        this.countryResolver = countryResolver;
        this.deviceResolver = deviceResolver;
        this.securityVerificationService = securityVerificationService;
    }

    /**
     * Builds a PolicyContext from a loaded NewUrl entity during normal serving evaluation.
     */
    public PolicyContext createContext(NewUrl entity, HttpServletRequest request) {
        if (entity == null) {
            return null;
        }

        return createContext(
                entity.getId(),
                entity.getNewUrl(),
                entity.getOriginalUrl(),
                entity.isActive(),
                entity.getExpireAt(),
                entity.getUsageLimit(),
                entity.getClickCount(),
                request
        );
    }

    /**
     * Builds a PolicyContext from direct values (e.g. from LRU cache).
     */
    public PolicyContext createContext(
            Long shortUrlId,
            String newUrl,
            String originalUrl,
            boolean active,
            Instant expireAt,
            Long usageLimit,
            long clickCount,
            HttpServletRequest request
    ) {
        Optional<AccessPolicy> accessPolicyOpt = shortUrlId != null
                ? accessPolicyRepository.findByShortUrlId(shortUrlId)
                : Optional.empty();

        Optional<UsagePolicy> usagePolicyOpt = shortUrlId != null
                ? usagePolicyRepository.findByShortUrlId(shortUrlId)
                : Optional.empty();

        String clientIp = clientIpResolver.resolveClientIp(request);
        String clientCountry = countryResolver.resolveCountry(request);
        DeviceInfo deviceInfo = deviceResolver.resolveDevice(request);
        String referer = request != null ? request.getHeader("Referer") : null;
        boolean verified = securityVerificationService.isVerifiedByCookie(request, shortUrlId);

        return new PolicyContext(
                shortUrlId,
                newUrl,
                originalUrl,
                active,
                expireAt,
                usageLimit,
                clickCount,
                accessPolicyOpt.orElse(null),
                usagePolicyOpt.orElse(null),
                request,
                clientIp,
                clientCountry,
                deviceInfo,
                referer,
                verified,
                null,
                null,
                false
        );
    }

    /**
     * Builds a PolicyContext specifically for credential verification (POST /**).
     */
    public PolicyContext createContextForVerification(
            NewUrl entity,
            String pin,
            String password,
            HttpServletRequest request
    ) {
        if (entity == null) {
            return null;
        }

        Optional<AccessPolicy> accessPolicyOpt = accessPolicyRepository.findByShortUrlId(entity.getId());
        Optional<UsagePolicy> usagePolicyOpt = usagePolicyRepository.findByShortUrlId(entity.getId());

        String clientIp = clientIpResolver.resolveClientIp(request);
        String clientCountry = countryResolver.resolveCountry(request);
        DeviceInfo deviceInfo = deviceResolver.resolveDevice(request);
        String referer = request != null ? request.getHeader("Referer") : null;
        boolean verified = securityVerificationService.isVerifiedByCookie(request, entity.getId());

        return new PolicyContext(
                entity.getId(),
                entity.getNewUrl(),
                entity.getOriginalUrl(),
                entity.isActive(),
                entity.getExpireAt(),
                entity.getUsageLimit(),
                entity.getClickCount(),
                accessPolicyOpt.orElse(null),
                usagePolicyOpt.orElse(null),
                request,
                clientIp,
                clientCountry,
                deviceInfo,
                referer,
                verified,
                pin,
                password,
                true
        );
    }
}
