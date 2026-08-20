package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.enums.TokenType;
import com.clinic.doc_appointment.util.CommittedSecretGuard;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Issues and verifies <em>access</em> tokens. Refresh tokens are opaque strings handled by
 * {@link RefreshTokenService} and never pass through here.
 */
@Service
@Slf4j
public class JwtService {

    /** The committed development secret. Fine locally, never acceptable in production. */
    private static final String DEFAULT_DEV_SECRET =
            "doc-appointment-super-secret-key-change-in-production-2024";

    /** HS256 requires a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    private final String secretKey;
    private final long jwtExpiration;
    private final boolean requireTokenType;
    private final String activeProfiles;

    /**
     * Constructor injection (rather than {@code @Value} fields) so the class can be unit-tested
     * without {@code ReflectionTestUtils}, and to match {@link LoginRateLimiter}.
     */
    public JwtService(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") long jwtExpiration,
            @Value("${jwt.require-token-type:false}") boolean requireTokenType,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.secretKey = secretKey;
        this.jwtExpiration = jwtExpiration;
        this.requireTokenType = requireTokenType;
        this.activeProfiles = activeProfiles;
    }

    /**
     * Refuses to start on a key that is too short to sign with, or on the committed default outside
     * the {@code dev} profile.
     *
     * <p>This used to only log an ERROR for the committed default, on the reasoning that throwing
     * would break a developer's first run. The {@code dev} profile exemption gives developers that
     * without leaving production one unset environment variable away from a signing key that anyone
     * with repository access can read.
     */
    @PostConstruct
    void validateSecret() {
        CommittedSecretGuard.requireUsableSecret(
                "jwt.secret", "JWT_SECRET", secretKey, DEFAULT_DEV_SECRET, MIN_SECRET_BYTES, activeProfiles);

        if (DEFAULT_DEV_SECRET.equals(secretKey)) {
            log.warn("jwt.secret is the committed development default; permitted only because the "
                    + "'dev' profile is active. Never run this configuration anywhere else.");
        }
    }

    public String generateToken(UserPrincipal userPrincipal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", userPrincipal.getRole());
        claims.put("userId", userPrincipal.getId());
        // Payload claim named "typ" — distinct from the JOSE header "typ", which jjwt sets itself.
        claims.put("typ", TokenType.ACCESS.getClaimValue());
        return buildToken(claims, userPrincipal.getEmail(), jwtExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * True when {@code token} is a live <em>access</em> token belonging to {@code userDetails}.
     *
     * <p>The token-type check lives here, rather than at each call site, so {@link JwtAuthFilter}
     * and {@link WebSocketAuthInterceptor} both get it with no chance of one being missed.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (!isAcceptableAccessType(extractTokenType(token))) {
            return false;
        }
        final String email = extractEmail(token);
        // Null subject is reachable: a patient may register without an email, producing a
        // token with no `sub`. Guard rather than NPE into the filter's catch block.
        return email != null && email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Tokens issued before the {@code typ} claim existed carry no type. They are accepted while
     * {@code jwt.require-token-type} is false so a deploy does not log everyone out; flip the
     * flag once those tokens have aged out.
     */
    private boolean isAcceptableAccessType(String tokenType) {
        if (tokenType == null) {
            return !requireTokenType;
        }
        return TokenType.ACCESS.getClaimValue().equals(tokenType);
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class));
    }

    /** Value of the {@code typ} payload claim, or null for a token issued before it existed. */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("typ", String.class));
    }

    /** Access-token lifetime in seconds, for the {@code expiresIn} field of the auth response. */
    public long getAccessExpirationSeconds() {
        return jwtExpiration / 1000L;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
