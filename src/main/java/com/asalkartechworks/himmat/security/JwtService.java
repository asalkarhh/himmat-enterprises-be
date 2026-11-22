package com.asalkartechworks.himmat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${app.security.jwt.secret:}") String secret,
            @Value("${app.security.jwt.expiration-seconds:1800}") long expirationSeconds,
            @Value("${app.security.jwt.issuer:himmat-enterprises}") String issuer,
            @Value("${app.security.jwt.audience:himmat-app}") String audience) {

        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException(
                    "Security configuration error: 'app.security.jwt.secret' (APP_SECURITY_JWT_SECRET) " +
                    "must be set and contain at least 32 characters (256 bits)."
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(secret.trim().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateToken(UserPrincipal principal) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .claim("userId", principal.getId())
                .claim("username", principal.getUsername())
                .claim("role", principal.getRole().name())
                .claim("userVersion", principal.getVersion())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationSeconds * 1000))
                .signWith(signingKey)
                .compact();
    }

    public Long extractUserId(String token) {
        Object userIdObj = extractClaim(token, claims -> claims.get("userId"));
        if (userIdObj instanceof Number num) {
            return num.longValue();
        } else if (userIdObj != null) {
            return Long.parseLong(userIdObj.toString());
        }
        String sub = extractClaim(token, Claims::getSubject);
        return Long.parseLong(sub);
    }

    public String extractUsername(String token) {
        String username = extractClaim(token, claims -> claims.get("username", String.class));
        if (username != null) {
            return username;
        }
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Long extractUserVersion(String token) {
        Object versionObj = extractClaim(token, claims -> claims.get("userVersion"));
        if (versionObj instanceof Number num) {
            return num.longValue();
        } else if (versionObj != null) {
            return Long.parseLong(versionObj.toString());
        }
        return null;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Invalid JWT signature or format: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
