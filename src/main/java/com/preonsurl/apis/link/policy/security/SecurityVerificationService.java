package com.preonsurl.apis.link.policy.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Manages the issuance and verification of temporary security challenge authorization cookies.
 */
@Service
public class SecurityVerificationService {

    public static final String COOKIE_PREFIX = "PREONS_SEC_";
    public static final String VERIFIED_VALUE = "VERIFIED";
    public static final long DEFAULT_MAX_AGE_SECONDS = 600; // 10 minutes

    /**
     * Determines whether the incoming request presents a valid, authenticated security cookie
     * for the given short link identifier.
     *
     * @param request Incoming HTTP servlet request
     * @param shortUrlId The unique ID of the short URL
     * @return true if authenticated, false otherwise
     */
    public boolean isVerifiedByCookie(HttpServletRequest request, Long shortUrlId) {
        if (request == null || request.getCookies() == null || shortUrlId == null) {
            return false;
        }

        String cookieName = COOKIE_PREFIX + shortUrlId;
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName()) && VERIFIED_VALUE.equals(cookie.getValue())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generates an HTTP-only, secure authentication cookie after successful credential challenge.
     *
     * @param shortUrlId The unique ID of the short URL
     * @return ResponseCookie with 10-minute validity
     */
    public ResponseCookie createVerificationCookie(Long shortUrlId) {
        return ResponseCookie.from(COOKIE_PREFIX + shortUrlId, VERIFIED_VALUE)
                .path("/")
                .maxAge(DEFAULT_MAX_AGE_SECONDS)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
    }

    /**
     * Generates a cookie invalidation header to revoke verified session.
     */
    public ResponseCookie clearVerificationCookie(Long shortUrlId) {
        return ResponseCookie.from(COOKIE_PREFIX + shortUrlId, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
    }
}
