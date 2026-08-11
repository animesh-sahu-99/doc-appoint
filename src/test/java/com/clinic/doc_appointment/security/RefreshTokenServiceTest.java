package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.entity.RefreshToken;
import com.clinic.doc_appointment.enums.RevocationReason;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.InvalidRefreshTokenException;
import com.clinic.doc_appointment.repository.RefreshTokenRepository;
import com.clinic.doc_appointment.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the rotation state machine. The four {@code rotate} paths are where every interesting
 * failure lives, so each has a dedicated test — in particular the two that must <em>not</em>
 * revoke a family (expiry, and a legitimate concurrent refresh).
 */
class RefreshTokenServiceTest {

    private static final long THIRTY_DAYS_MS = 2_592_000_000L;
    private static final long NINETY_DAYS_MS = 7_776_000_000L;
    private static final long GRACE_SECONDS = 15L;
    private static final int MAX_FAMILIES = 10;
    private static final int RETAIN_REVOKED_DAYS = 7;

    private static final String USER_ID = "PAT-abc";
    private static final String FAMILY_ID = "family-1";
    private static final String RAW_TOKEN = "some-opaque-refresh-token";

    private RefreshTokenRepository repository;
    private RefreshPrincipalResolver principalResolver;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        principalResolver = mock(RefreshPrincipalResolver.class);
        service = newService(false);

        UserPrincipal principal =
                new UserPrincipal(USER_ID, "asha@example.com", "hashed", Role.PATIENT.authority());
        when(principalResolver.resolve(anyString(), any()))
                .thenReturn(new RefreshPrincipalResolver.ResolvedUser(principal, "Asha"));
    }

    private RefreshTokenService newService(boolean reuseRevokesAllUserTokens) {
        return new RefreshTokenService(repository, principalResolver,
                THIRTY_DAYS_MS, NINETY_DAYS_MS, GRACE_SECONDS,
                MAX_FAMILIES, RETAIN_REVOKED_DAYS, reuseRevokesAllUserTokens);
    }

    private RefreshToken row(Instant revokedAt, RevocationReason reason, Instant absoluteExpiresAt) {
        return RefreshToken.builder()
                .id("row-1")
                .tokenHash(TokenHasher.sha256Hex(RAW_TOKEN))
                .familyId(FAMILY_ID)
                .userId(USER_ID)
                .role(Role.PATIENT.authority())
                .userEmail("asha@example.com")
                .issuedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .absoluteExpiresAt(absoluteExpiresAt)
                .revokedAt(revokedAt)
                .revokedReason(reason)
                .build();
    }

    private RefreshToken liveRow() {
        return row(null, null, Instant.now().plus(90, ChronoUnit.DAYS));
    }

    private void stubLookup(RefreshToken stored) {
        when(repository.findByTokenHash(TokenHasher.sha256Hex(RAW_TOKEN)))
                .thenReturn(Optional.ofNullable(stored));
    }

    private void stubConsume(int affectedRows) {
        when(repository.consumeIfLive(anyString(), eq(RevocationReason.ROTATED), any()))
                .thenReturn(affectedRows);
    }

    private RefreshToken captureSaved() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    // ===================== path A: normal rotation =====================

    @Test
    void rotationIssuesSuccessorInSameFamilyAndCarriesAbsoluteCapForward() {
        RefreshToken stored = liveRow();
        stubConsume(1);
        stubLookup(stored);

        RefreshTokenService.RotationResult result = service.rotate(RAW_TOKEN, TokenContext.EMPTY);

        RefreshToken successor = captureSaved();
        assertEquals(FAMILY_ID, successor.getFamilyId(), "successor stays in the same session family");
        assertEquals(USER_ID, successor.getUserId());
        assertEquals(stored.getAbsoluteExpiresAt(), successor.getAbsoluteExpiresAt(),
                "the 90-day cap must be copied unchanged, not renewed");
        assertEquals("Asha", result.user().displayName());
        verify(repository, never()).revokeFamily(anyString(), any(), any());
    }

    @Test
    void rotationStoresOnlyTheHashAndReturnsTheRawTokenOnce() {
        stubConsume(1);
        stubLookup(liveRow());

        RefreshTokenService.RotationResult result = service.rotate(RAW_TOKEN, TokenContext.EMPTY);
        String raw = result.refreshToken().rawToken();

        RefreshToken successor = captureSaved();
        assertNotEquals(raw, successor.getTokenHash(), "the raw token must never be persisted");
        assertEquals(TokenHasher.sha256Hex(raw), successor.getTokenHash());
    }

    @Test
    void successorGetsSlidingExpiryCappedByTheAbsoluteDeadline() {
        // Family expires in 1 hour, well before the 30-day sliding window would.
        Instant nearCap = Instant.now().plus(1, ChronoUnit.HOURS);
        stubConsume(1);
        stubLookup(row(null, null, nearCap));

        service.rotate(RAW_TOKEN, TokenContext.EMPTY);

        RefreshToken successor = captureSaved();
        assertEquals(nearCap, successor.getExpiresAt(),
                "sliding expiry must be clamped to the family's absolute cap");
    }

    // ===================== path B: expired =====================

    @Test
    void expiredTokenIsRejectedWithoutRevokingTheFamily() {
        stubConsume(0);
        stubLookup(liveRow());   // never revoked — the CAS failed on the expiry predicate

        assertThrows(InvalidRefreshTokenException.class,
                () -> service.rotate(RAW_TOKEN, TokenContext.EMPTY));

        // Expiry is not an attack. Revoking here would log out an honest user's other tabs.
        verify(repository, never()).revokeFamily(anyString(), any(), any());
        verify(repository, never()).revokeAllForUser(anyString(), any(), any());
        verify(repository, never()).save(any());
    }

    // ===================== path C: concurrent refresh inside the grace window =====================

    @Test
    void concurrentRefreshWithinGraceWindowIssuesSiblingWithoutRevoking() {
        stubConsume(0);
        stubLookup(row(Instant.now().minusSeconds(GRACE_SECONDS - 5),
                RevocationReason.ROTATED, Instant.now().plus(90, ChronoUnit.DAYS)));

        RefreshTokenService.RotationResult result = service.rotate(RAW_TOKEN, TokenContext.EMPTY);

        // Regression guard: without the grace window, every client that fires parallel requests
        // would have its whole session revoked as a false-positive replay.
        assertEquals(FAMILY_ID, result.refreshToken().familyId());
        verify(repository).save(any(RefreshToken.class));
        verify(repository, never()).revokeFamily(anyString(), any(), any());
    }

    // ===================== path D: reuse detected =====================

    @Test
    void replayOutsideGraceWindowRevokesTheFamilyAndThrows() {
        stubConsume(0);
        stubLookup(row(Instant.now().minusSeconds(GRACE_SECONDS + 60),
                RevocationReason.ROTATED, Instant.now().plus(90, ChronoUnit.DAYS)));

        assertThrows(InvalidRefreshTokenException.class,
                () -> service.rotate(RAW_TOKEN, TokenContext.EMPTY));

        verify(repository, times(1))
                .revokeFamily(eq(FAMILY_ID), eq(RevocationReason.REUSE_DETECTED), any());
        verify(repository, never()).revokeAllForUser(anyString(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void replayAlsoRevokesEveryUserSessionWhenConfiguredTo() {
        service = newService(true);
        stubConsume(0);
        stubLookup(row(Instant.now().minusSeconds(GRACE_SECONDS + 60),
                RevocationReason.ROTATED, Instant.now().plus(90, ChronoUnit.DAYS)));

        assertThrows(InvalidRefreshTokenException.class,
                () -> service.rotate(RAW_TOKEN, TokenContext.EMPTY));

        verify(repository).revokeFamily(eq(FAMILY_ID), eq(RevocationReason.REUSE_DETECTED), any());
        verify(repository).revokeAllForUser(eq(USER_ID), eq(RevocationReason.REUSE_DETECTED), any());
    }

    @Test
    void tokenRevokedByLogoutIsRejectedWithoutTriggeringReuseHandling() {
        stubConsume(0);
        stubLookup(row(Instant.now().minusSeconds(600),
                RevocationReason.LOGOUT, Instant.now().plus(90, ChronoUnit.DAYS)));

        assertThrows(InvalidRefreshTokenException.class,
                () -> service.rotate(RAW_TOKEN, TokenContext.EMPTY));

        // Already deliberately revoked — nothing further to revoke, and it is not an attack.
        verify(repository, never()).revokeFamily(anyString(), any(), any());
    }

    // ===================== unknown / capped =====================

    @Test
    void unknownTokenIsRejectedWithNoSideEffects() {
        stubConsume(0);
        stubLookup(null);

        assertThrows(InvalidRefreshTokenException.class,
                () -> service.rotate(RAW_TOKEN, TokenContext.EMPTY));

        verify(repository, never()).revokeFamily(anyString(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void familyPastItsAbsoluteCapCannotRotateEvenWhenTheRowIsLive() {
        stubConsume(1);
        stubLookup(row(null, null, Instant.now().minusSeconds(60)));   // cap already elapsed

        assertThrows(InvalidRefreshTokenException.class,
                () -> service.rotate(RAW_TOKEN, TokenContext.EMPTY));

        verify(repository, never()).save(any());
    }

    // ===================== issuance =====================

    @Test
    void newFamilyGetsAFreshFamilyIdAndAnAbsoluteCap() {
        when(repository.findLiveFamilyIds(anyString(), any())).thenReturn(List.of());
        UserPrincipal principal =
                new UserPrincipal(USER_ID, "asha@example.com", "hashed", Role.PATIENT.authority());

        RefreshTokenService.IssuedRefreshToken issued =
                service.issueNewFamily(principal, TokenContext.EMPTY);

        RefreshToken saved = captureSaved();
        assertEquals(issued.familyId(), saved.getFamilyId());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(Role.PATIENT.authority(), saved.getRole());
        assertTrue(saved.getAbsoluteExpiresAt().isAfter(saved.getExpiresAt().minusSeconds(1)),
                "absolute cap must not sit before the sliding expiry");
        verify(repository, never()).revokeFamily(anyString(), any(), any());
    }

    @Test
    void reachingTheActiveSessionCapRevokesTheOldestFamily() {
        List<String> live = List.of("oldest", "middle", "newest");
        RefreshTokenService capped = new RefreshTokenService(repository, principalResolver,
                THIRTY_DAYS_MS, NINETY_DAYS_MS, GRACE_SECONDS, 3, RETAIN_REVOKED_DAYS, false);
        when(repository.findLiveFamilyIds(anyString(), any())).thenReturn(live);

        capped.issueNewFamily(
                new UserPrincipal(USER_ID, "asha@example.com", "hashed", Role.PATIENT.authority()),
                TokenContext.EMPTY);

        // Cap of 3 with 3 already live: exactly one must go, and it must be the oldest.
        verify(repository, times(1))
                .revokeFamily(eq("oldest"), eq(RevocationReason.SUPERSEDED_BY_CAP), any());
        verify(repository, never()).revokeFamily(eq("middle"), any(), any());
        verify(repository, never()).revokeFamily(eq("newest"), any(), any());
    }

    @Test
    void issuingBelowTheCapRevokesNothing() {
        when(repository.findLiveFamilyIds(anyString(), any())).thenReturn(List.of("only-one"));

        service.issueNewFamily(
                new UserPrincipal(USER_ID, "asha@example.com", "hashed", Role.PATIENT.authority()),
                TokenContext.EMPTY);

        verify(repository, never()).revokeFamily(anyString(), any(), any());
    }

    // ===================== revocation =====================

    @Test
    void logoutRevokesTheWholeFamily() {
        stubLookup(liveRow());

        service.revokeFamilyOf(RAW_TOKEN, RevocationReason.LOGOUT);

        verify(repository).revokeFamily(eq(FAMILY_ID), eq(RevocationReason.LOGOUT), any());
    }

    @Test
    void logoutAllRevokesEverySessionForTheUser() {
        stubLookup(liveRow());

        service.revokeAllForUserOf(RAW_TOKEN, RevocationReason.LOGOUT_ALL);

        verify(repository).revokeAllForUser(eq(USER_ID), eq(RevocationReason.LOGOUT_ALL), any());
    }

    @Test
    void logoutOfAnUnknownTokenIsASilentNoOp() {
        stubLookup(null);

        // Idempotent by design: a retried logout must not error, and the caller must not learn
        // whether the token existed.
        service.revokeFamilyOf(RAW_TOKEN, RevocationReason.LOGOUT);

        verify(repository, never()).revokeFamily(anyString(), any(), any());
    }

    // ===================== housekeeping =====================

    @Test
    void sweepRetainsRevokedRowsLongEnoughForReuseDetectionToStillWork() {
        Instant before = Instant.now();

        service.purgeExpiredAndStaleRevoked();

        ArgumentCaptor<Instant> expiredCutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> revokedCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteExpiredBefore(expiredCutoff.capture());
        verify(repository).deleteRevokedBefore(revokedCutoff.capture());

        long retainedDays = Duration.between(revokedCutoff.getValue(), expiredCutoff.getValue()).toDays();
        assertEquals(RETAIN_REVOKED_DAYS, retainedDays,
                "revoked rows must outlive expiry by the retention window, or replays stop being detectable");
        assertTrue(!expiredCutoff.getValue().isBefore(before));
    }
}
