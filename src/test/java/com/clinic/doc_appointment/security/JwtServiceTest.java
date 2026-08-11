package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.enums.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "unit-test-signing-secret-key-of-sufficient-length-2026";
    private static final String OTHER_SECRET = "a-completely-different-signing-secret-key-2026-abcdef";
    private static final long ONE_HOUR = 3_600_000L;

    private JwtService jwtService;
    private UserPrincipal patient;

    @BeforeEach
    void setUp() {
        jwtService = newService(SECRET, ONE_HOUR, false);
        patient = new UserPrincipal("PAT-123", "asha@example.com", "hashed", Role.PATIENT.authority());
    }

    private static JwtService newService(String secret, long expiration, boolean requireTokenType) {
        JwtService service = new JwtService(secret, expiration, requireTokenType);
        service.validateSecret();
        return service;
    }

    private static SecretKey keyOf(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Builds a token directly, bypassing JwtService, so we can forge claims and expiry. */
    private static String forge(String secret, String subject, Map<String, Object> claims, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(keyOf(secret))
                .compact();
    }

    // ===================== claims =====================

    @Test
    void generatedTokenCarriesSubjectRoleUserIdAndAccessType() {
        String token = jwtService.generateToken(patient);

        assertEquals("asha@example.com", jwtService.extractEmail(token), "subject is the email");
        assertEquals("ROLE_PATIENT", jwtService.extractRole(token));
        assertEquals("PAT-123", jwtService.extractUserId(token));
        assertEquals("access", jwtService.extractTokenType(token));
    }

    @Test
    void generatedTokenIsValidForItsOwner() {
        assertTrue(jwtService.isTokenValid(jwtService.generateToken(patient), patient));
    }

    @Test
    void tokenIsNotValidForADifferentUser() {
        UserPrincipal other = new UserPrincipal("PAT-999", "other@example.com", "hashed", Role.PATIENT.authority());
        assertFalse(jwtService.isTokenValid(jwtService.generateToken(patient), other));
    }

    // ===================== token type =====================

    @Test
    void refreshTypedTokenIsRejectedAsAnAccessToken() {
        String forged = forge(SECRET, patient.getEmail(),
                Map.of("role", "ROLE_PATIENT", "userId", "PAT-123", "typ", "refresh"), ONE_HOUR);

        assertFalse(jwtService.isTokenValid(forged, patient),
                "a refresh-typed JWT must never authenticate a request");
    }

    @Test
    void legacyTokenWithoutTypeClaimIsAcceptedWhenNotRequired() {
        String legacy = forge(SECRET, patient.getEmail(),
                Map.of("role", "ROLE_PATIENT", "userId", "PAT-123"), ONE_HOUR);

        assertNull(jwtService.extractTokenType(legacy));
        assertTrue(jwtService.isTokenValid(legacy, patient),
                "tokens issued before the typ claim must keep working during rollout");
    }

    @Test
    void legacyTokenWithoutTypeClaimIsRejectedWhenRequired() {
        JwtService strict = newService(SECRET, ONE_HOUR, true);
        String legacy = forge(SECRET, patient.getEmail(),
                Map.of("role", "ROLE_PATIENT", "userId", "PAT-123"), ONE_HOUR);

        assertFalse(strict.isTokenValid(legacy, patient));
    }

    // ===================== expiry and signature =====================

    @Test
    void expiredTokenThrowsOnParse() {
        String expired = forge(SECRET, patient.getEmail(),
                Map.of("role", "ROLE_PATIENT", "userId", "PAT-123", "typ", "access"), -1000L);

        assertThrows(ExpiredJwtException.class, () -> jwtService.extractEmail(expired));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = forge(OTHER_SECRET, patient.getEmail(),
                Map.of("role", "ROLE_PATIENT", "userId", "PAT-123", "typ", "access"), ONE_HOUR);

        assertThrows(SignatureException.class, () -> jwtService.extractEmail(foreign));
    }

    // ===================== null subject (email-less patient) =====================

    @Test
    void subjectlessTokenIsRejectedWithoutNullPointer() {
        String subjectless = forge(SECRET, null,
                Map.of("role", "ROLE_PATIENT", "userId", "PAT-123", "typ", "access"), ONE_HOUR);

        // Regression: this previously threw NPE inside isTokenValid, swallowed by the filter.
        assertFalse(jwtService.isTokenValid(subjectless, patient));
    }

    // ===================== configuration guards =====================

    @Test
    void rejectsSecretShorterThanTheHmacMinimum() {
        JwtService weak = new JwtService("too-short", ONE_HOUR, false);
        assertThrows(IllegalStateException.class, weak::validateSecret);
    }

    @Test
    void acceptsSecretAtExactlyTheHmacMinimum() {
        JwtService boundary = new JwtService("0123456789abcdef0123456789abcdef", ONE_HOUR, false);
        boundary.validateSecret();   // 32 bytes — must not throw
    }

    @Test
    void exposesAccessExpirationInSeconds() {
        assertEquals(3600L, jwtService.getAccessExpirationSeconds());
    }
}
