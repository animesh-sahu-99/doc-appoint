package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.entity.RefreshToken;
import com.clinic.doc_appointment.enums.RevocationReason;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.InvalidRefreshTokenException;
import com.clinic.doc_appointment.repository.RefreshTokenRepository;
import com.clinic.doc_appointment.util.TokenHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>Tokens are opaque 256-bit random strings, stored only as a SHA-256 hash, and rotated on
 * every use: exchanging one revokes it and mints a successor in the same family. Replaying a
 * consumed token is therefore evidence the session was copied, and revokes the family.
 */
@Service
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 256 bits — far beyond brute-force reach, so the stored digest needs no salt or stretching. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshPrincipalResolver principalResolver;

    private final long refreshExpirationMillis;
    private final long absoluteExpirationMillis;
    private final long graceSeconds;
    private final int maxActivePerUser;
    private final int retainRevokedDays;
    private final boolean reuseRevokesAllUserTokens;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshPrincipalResolver principalResolver,
            @Value("${jwt.refresh.expiration:2592000000}") long refreshExpirationMillis,
            @Value("${jwt.refresh.absolute-expiration:7776000000}") long absoluteExpirationMillis,
            @Value("${jwt.refresh.grace-seconds:15}") long graceSeconds,
            @Value("${jwt.refresh.max-active-per-user:10}") int maxActivePerUser,
            @Value("${jwt.refresh.retain-revoked-days:7}") int retainRevokedDays,
            @Value("${jwt.refresh.reuse-revokes-all-user-tokens:false}") boolean reuseRevokesAllUserTokens) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.principalResolver = principalResolver;
        this.refreshExpirationMillis = refreshExpirationMillis;
        this.absoluteExpirationMillis = absoluteExpirationMillis;
        this.graceSeconds = graceSeconds;
        this.maxActivePerUser = maxActivePerUser;
        this.retainRevokedDays = retainRevokedDays;
        this.reuseRevokesAllUserTokens = reuseRevokesAllUserTokens;
    }

    /** A freshly minted token. {@code rawToken} exists only here and in the HTTP response. */
    public record IssuedRefreshToken(String rawToken, Instant expiresAt, String familyId) {
    }

    /** Outcome of a successful exchange: who the token belongs to, and its replacement. */
    public record RotationResult(RefreshPrincipalResolver.ResolvedUser user,
                                 IssuedRefreshToken refreshToken) {
    }

    // ===================== issuance =====================

    /**
     * Starts a new device session. Called on login and registration.
     *
     * <p>Enforces the per-user cap first, revoking oldest sessions so a user cannot accumulate
     * unbounded live refresh tokens.
     */
    @Transactional
    public IssuedRefreshToken issueNewFamily(UserPrincipal principal, TokenContext context) {
        Instant now = Instant.now();
        enforceActiveFamilyCap(principal.getId(), now);

        String familyId = UUID.randomUUID().toString();
        Instant absoluteExpiresAt = now.plusMillis(absoluteExpirationMillis);

        return persistToken(RefreshToken.builder()
                .familyId(familyId)
                .userId(principal.getId())
                .role(principal.getRole())
                .userEmail(principal.getEmail()), absoluteExpiresAt, context, now);
    }

    /**
     * Exchanges a refresh token for its successor.
     *
     * <p><strong>{@code noRollbackFor} is load-bearing.</strong> The reuse branch revokes the
     * family and then throws. Without it Spring would mark the transaction rollback-only and
     * silently undo that revocation — the server would log a detected replay and revoke nothing.
     * No other throwing path here writes, so the attribute is harmless elsewhere.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotationResult rotate(String rawToken, TokenContext context) {
        String hash = TokenHasher.sha256Hex(rawToken);
        Instant now = Instant.now();

        // (A) Atomic consume: either this caller wins the rotation, or it does not.
        if (refreshTokenRepository.consumeIfLive(hash, RevocationReason.ROTATED, now) == 1) {
            RefreshToken consumed = requireRow(hash);
            return succeed(consumed, context, now);
        }

        // The CAS matched nothing. Re-read to find out why.
        RefreshToken row = requireRow(hash);

        // (B) Still live but the CAS failed on the expiry predicate: simply too old.
        //     An expired token is not an attack — do not revoke the family for it.
        if (row.getRevokedAt() == null) {
            throw new InvalidRefreshTokenException("expired: family=" + row.getFamilyId());
        }

        boolean consumedByRotation = row.getRevokedReason() == RevocationReason.ROTATED;
        boolean withinGrace = consumedByRotation
                && Duration.between(row.getRevokedAt(), now).getSeconds() <= graceSeconds;

        // (C) A second refresh from the same device moments after the first. Legitimate:
        //     the client fired parallel requests. Issue a sibling successor in the same family.
        //     Both successors stay valid; the client keeps whichever response arrives last and
        //     the other lapses. We cannot revoke the sibling — we do not know which one it kept.
        if (withinGrace) {
            log.info("Refresh within grace window: family={} user={}", row.getFamilyId(), row.getUserId());
            return succeed(row, context, now);
        }

        // (D) A consumed token replayed long after its rotation: the session has been copied.
        if (consumedByRotation) {
            log.warn("Refresh token REUSE detected — revoking family. user={} family={} ip={}",
                    row.getUserId(), row.getFamilyId(), context.ip());
            refreshTokenRepository.revokeFamily(row.getFamilyId(), RevocationReason.REUSE_DETECTED, now);
            if (reuseRevokesAllUserTokens) {
                refreshTokenRepository.revokeAllForUser(row.getUserId(), RevocationReason.REUSE_DETECTED, now);
            }
        }

        // Otherwise the token was revoked by an explicit logout or the family cap.
        throw new InvalidRefreshTokenException(
                "revoked (" + row.getRevokedReason() + "): family=" + row.getFamilyId());
    }

    /** Resolve the owner and mint the successor for a token we have decided to honour. */
    private RotationResult succeed(RefreshToken predecessor, TokenContext context, Instant now) {
        RefreshPrincipalResolver.ResolvedUser user = principalResolver.resolve(
                predecessor.getUserId(), Role.fromAuthority(predecessor.getRole()));
        return new RotationResult(user, mintSuccessor(predecessor, context, now));
    }

    /**
     * Inserts the replacement token, carrying the family and its absolute cap forward unchanged.
     * The sliding expiry is renewed but can never outlive the cap.
     */
    private IssuedRefreshToken mintSuccessor(RefreshToken predecessor, TokenContext context, Instant now) {
        if (!predecessor.getAbsoluteExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException(
                    "absolute session cap reached: family=" + predecessor.getFamilyId());
        }
        return persistToken(RefreshToken.builder()
                .familyId(predecessor.getFamilyId())
                .userId(predecessor.getUserId())
                .role(predecessor.getRole())
                .userEmail(predecessor.getUserEmail()),
                predecessor.getAbsoluteExpiresAt(), context, now);
    }

    /**
     * Generates the raw secret, stores only its hash, and returns the plaintext.
     * This is the sole moment the raw token exists server-side: never log it, never persist it,
     * never echo it into an error.
     */
    private IssuedRefreshToken persistToken(RefreshToken.RefreshTokenBuilder builder,
                                            Instant absoluteExpiresAt,
                                            TokenContext context,
                                            Instant now) {
        byte[] entropy = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(entropy);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);

        // Sliding renewal, but never past the family's absolute cap.
        Instant sliding = now.plusMillis(refreshExpirationMillis);
        Instant expiresAt = sliding.isBefore(absoluteExpiresAt) ? sliding : absoluteExpiresAt;

        RefreshToken token = builder
                .tokenHash(TokenHasher.sha256Hex(rawToken))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .absoluteExpiresAt(absoluteExpiresAt)
                .deviceType(context.deviceType())
                .userAgent(context.userAgent())
                .createdIp(context.ip())
                .build();

        refreshTokenRepository.save(token);
        return new IssuedRefreshToken(rawToken, token.getExpiresAt(), token.getFamilyId());
    }

    // ===================== revocation =====================

    /** Revokes the device session a token belongs to. Silent no-op if the token is unknown. */
    @Transactional
    public void revokeFamilyOf(String rawToken, RevocationReason reason) {
        findRow(rawToken).ifPresent(row -> {
            int revoked = refreshTokenRepository.revokeFamily(row.getFamilyId(), reason, Instant.now());
            log.info("Revoked {} token(s) in family {} ({})", revoked, row.getFamilyId(), reason);
        });
    }

    /** Revokes every session the token's owner holds. Silent no-op if the token is unknown. */
    @Transactional
    public void revokeAllForUserOf(String rawToken, RevocationReason reason) {
        findRow(rawToken).ifPresent(row -> {
            int revoked = refreshTokenRepository.revokeAllForUser(row.getUserId(), reason, Instant.now());
            log.info("Revoked {} token(s) for user {} ({})", revoked, row.getUserId(), reason);
        });
    }

    // ===================== housekeeping =====================

    /**
     * Deletes tokens past their expiry, and revoked rows older than the retention window.
     *
     * <p>Runs on every instance; the deletes are idempotent so that is harmless — the same
     * property {@link LoginRateLimiter#evictStale()} relies on.
     */
    @Scheduled(fixedDelayString = "${jwt.refresh.cleanup-millis:3600000}")
    @Transactional
    public void purgeExpiredAndStaleRevoked() {
        Instant now = Instant.now();
        int expired = refreshTokenRepository.deleteExpiredBefore(now);
        int revoked = refreshTokenRepository.deleteRevokedBefore(
                now.minus(retainRevokedDays, ChronoUnit.DAYS));
        if (expired + revoked > 0) {
            log.info("Refresh token sweep: removed {} expired and {} stale revoked row(s)", expired, revoked);
        }
    }

    // ===================== helpers =====================

    private Optional<RefreshToken> findRow(String rawToken) {
        return refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken));
    }

    private RefreshToken requireRow(String hash) {
        return refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("unknown refresh token"));
    }

    /**
     * Revokes oldest sessions until the user is below the active-family cap, leaving room for
     * the one about to be created.
     */
    private void enforceActiveFamilyCap(String userId, Instant now) {
        List<String> liveFamilies = refreshTokenRepository.findLiveFamilyIds(userId, now);
        int excess = liveFamilies.size() - maxActivePerUser + 1;
        for (int i = 0; i < excess && i < liveFamilies.size(); i++) {
            String oldest = liveFamilies.get(i);
            refreshTokenRepository.revokeFamily(oldest, RevocationReason.SUPERSEDED_BY_CAP, now);
            log.info("Active session cap ({}) reached for user {} — revoked oldest family {}",
                    maxActivePerUser, userId, oldest);
        }
    }
}
