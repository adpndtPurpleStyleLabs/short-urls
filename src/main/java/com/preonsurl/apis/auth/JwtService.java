package com.preonsurl.apis.auth;

import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.coreconfig.constants.CoreConfigKeys;
import com.preonsurl.coreconfig.service.CoreConfigService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    public final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtService(CoreConfigService coreConfigService) {
        String secret = coreConfigService.get(CoreConfigKeys.Security.JWT_SECRET);
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            try {
                keyBytes = java.security.MessageDigest.getInstance("SHA-256").digest(keyBytes);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = coreConfigService.getLong(CoreConfigKeys.Security.JWT_EXPIRATION_SECONDS);
    }

    public String generateToken(User user) {
        Instant instant = Instant.now();
        Instant expire = instant.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("tenantId", user.getTenantId())
                .issuedAt(Date.from(instant))
                .expiration(Date.from(expire))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        return extractClaims(token)
                .get("userId", Long.class);
    }

    public Long extractTenantId(String token) {
        return extractClaims(token)
                .get("tenantId", Long.class);
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
