package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.RefreshToken;
import com.clinic.doc_appointment.enums.RevocationReason;
import com.clinic.doc_appointment.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the repository against a real (embedded) database.
 *
 * <p>The point is less the assertions than the fact that this boots at all: it proves the new
 * JPQL parses and the {@code refresh_tokens} mapping is valid, neither of which any mock-based
 * test can catch — and both of which would otherwise only surface at application startup.
 */
@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static final String USER_ID = "PAT-1";

    private RefreshToken persist(String hash, String familyId, Instant expiresAt, Instant revokedAt) {
        RefreshToken token = RefreshToken.builder()
                .tokenHash(hash)
                .familyId(familyId)
                .userId(USER_ID)
                .role(Role.PATIENT.authority())
                .userEmail("asha@example.com")
                .issuedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .expiresAt(expiresAt)
                .absoluteExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .revokedAt(revokedAt)
                .revokedReason(revokedAt == null ? null : RevocationReason.LOGOUT)
                .build();
        return entityManager.persistAndFlush(token);
    }

    private RefreshToken live(String hash, String familyId) {
        return persist(hash, familyId, Instant.now().plus(30, ChronoUnit.DAYS), null);
    }

    @Test
    void persistsAndFindsByHash() {
        live("hash-a", "fam-1");
        entityManager.clear();

        RefreshToken found = repository.findByTokenHash("hash-a").orElseThrow();
        assertNotNull(found.getId(), "UUID generator must assign an id");
        assertEquals("fam-1", found.getFamilyId());
        assertEquals(Role.PATIENT.authority(), found.getRole());
    }

    @Test
    void consumeIfLiveMarksTheRowRotatedAndIsNotRepeatable() {
        live("hash-b", "fam-1");
        entityManager.clear();
        Instant now = Instant.now();

        assertEquals(1, repository.consumeIfLive("hash-b", RevocationReason.ROTATED, now),
                "first caller wins the rotation");
        assertEquals(0, repository.consumeIfLive("hash-b", RevocationReason.ROTATED, now),
                "a replay must not win a second time");

        // Also proves @Modifying(clearAutomatically = true) works: without it this read would
        // return the stale pre-update entity from the persistence context.
        RefreshToken after = repository.findByTokenHash("hash-b").orElseThrow();
        assertNotNull(after.getRevokedAt());
        assertEquals(RevocationReason.ROTATED, after.getRevokedReason());
    }

    @Test
    void consumeIfLiveRefusesAnExpiredRow() {
        persist("hash-c", "fam-1", Instant.now().minus(1, ChronoUnit.DAYS), null);
        entityManager.clear();

        assertEquals(0, repository.consumeIfLive("hash-c", RevocationReason.ROTATED, Instant.now()));

        RefreshToken after = repository.findByTokenHash("hash-c").orElseThrow();
        assertNull(after.getRevokedAt(), "an expired row must not be marked rotated");
    }

    @Test
    void revokeFamilyAffectsOnlyLiveRowsOfThatFamily() {
        live("hash-d1", "fam-1");
        live("hash-d2", "fam-1");
        live("hash-d3", "fam-2");
        entityManager.clear();

        int revoked = repository.revokeFamily("fam-1", RevocationReason.REUSE_DETECTED, Instant.now());

        assertEquals(2, revoked);
        assertNull(repository.findByTokenHash("hash-d3").orElseThrow().getRevokedAt(),
                "a different session must be untouched");
    }

    @Test
    void revokeAllForUserRevokesEverySession() {
        live("hash-e1", "fam-1");
        live("hash-e2", "fam-2");
        entityManager.clear();

        assertEquals(2, repository.revokeAllForUser(USER_ID, RevocationReason.LOGOUT_ALL, Instant.now()));
    }

    /** The GROUP BY / ORDER BY MIN(...) query is the one most likely to fail to parse. */
    @Test
    void findLiveFamilyIdsReturnsDistinctFamiliesOldestFirst() {
        live("hash-f1", "fam-old");
        live("hash-f2", "fam-old");
        live("hash-f3", "fam-new");
        persist("hash-f4", "fam-revoked", Instant.now().plus(1, ChronoUnit.DAYS), Instant.now());
        entityManager.clear();

        List<String> families = repository.findLiveFamilyIds(USER_ID, Instant.now());

        assertEquals(2, families.size(), "each family counted once, revoked ones excluded");
        assertTrue(families.contains("fam-old"));
        assertTrue(families.contains("fam-new"));
    }

    @Test
    void sweepDeletesExpiredAndLongRevokedRows() {
        persist("hash-g1", "fam-1", Instant.now().minus(1, ChronoUnit.DAYS), null);
        persist("hash-g2", "fam-2", Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().minus(30, ChronoUnit.DAYS));
        live("hash-g3", "fam-3");
        entityManager.clear();

        assertEquals(1, repository.deleteExpiredBefore(Instant.now()));
        assertEquals(1, repository.deleteRevokedBefore(Instant.now().minus(7, ChronoUnit.DAYS)));

        entityManager.clear();
        assertTrue(repository.findByTokenHash("hash-g3").isPresent(), "the live row must survive");
    }
}
