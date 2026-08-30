package org.aisa.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.aisa.api.config.AisaProperties;
import org.springframework.stereotype.Service;

/**
 * Mints and verifies the admin access token.
 *
 * <p>The token carries the admin's id (subject) and username, and nothing else. It is
 * signed, not encrypted — anything put in it is readable by whoever holds it, so it holds
 * nothing that is not already on screen once the admin is logged in.
 */
@Service
public class JwtService {

    private static final String CLAIM_USERNAME = "username";

    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;

    public JwtService(AisaProperties properties) {
        AisaProperties.Jwt config = properties.jwt();
        byte[] secret = Decoders.BASE64.decode(config.secret());
        /*
         * HS256 requires >= 256 bits of key. jjwt would throw on the first sign() call
         * otherwise — i.e. on the first login attempt in production, long after deploy.
         * Failing here means a short key breaks startup, where it is obvious.
         */
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least 32 bytes. Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = config.issuer();
        this.ttl = config.accessTokenTtl();
    }

    public String issue(UUID adminId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(adminId.toString())
                .claim(CLAIM_USERNAME, username)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Duration ttl() {
        return ttl;
    }

    /**
     * Verifies signature, issuer and expiry.
     *
     * <p>Returns empty rather than throwing: an invalid token is the normal state of an
     * expired browser session, not an exceptional condition, and the filter must simply
     * leave the request anonymous.
     */
    public Optional<AuthenticatedAdmin> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedAdmin(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** The verified identity behind a request. */
    public record AuthenticatedAdmin(UUID id, String username) {}
}
