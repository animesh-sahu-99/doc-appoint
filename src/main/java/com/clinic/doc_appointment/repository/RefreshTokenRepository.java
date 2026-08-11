package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.RefreshToken;
import com.clinic.doc_appointment.enums.RevocationReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomically consume a live token: marks it {@code ROTATED} only if it is still unrevoked
     * and unexpired. Returns 1 when this caller won the rotation, 0 when someone else already
     * consumed it (or it had expired).
     *
     * <p>This must stay a compare-and-set rather than read → check → save. Under
     * {@code READ COMMITTED}, two concurrent updates against the same row serialize: the second
     * blocks, re-evaluates {@code revoked_at IS NULL} against the committed row, and matches
     * nothing. A read-then-write sequence instead leaves a window where both requests believe
     * they won, and one legitimate refresh gets misread as a replay attack.
     *
     * <p>{@code clearAutomatically} is load-bearing: bulk JPQL bypasses the persistence context,
     * so without it a {@link #findByTokenHash} immediately afterwards would return the stale,
     * pre-update entity from the first-level cache and report the row as still live.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revokedReason = :reason "
            + "WHERE t.tokenHash = :hash AND t.revokedAt IS NULL AND t.expiresAt > :now")
    int consumeIfLive(@Param("hash") String hash,
                      @Param("reason") RevocationReason reason,
                      @Param("now") Instant now);

    /** Revokes every live token in one device session. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revokedReason = :reason "
            + "WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") String familyId,
                     @Param("reason") RevocationReason reason,
                     @Param("now") Instant now);

    /** Revokes every live token the user holds, across all devices. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revokedReason = :reason "
            + "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") String userId,
                         @Param("reason") RevocationReason reason,
                         @Param("now") Instant now);

    /** Live session ids for a user, oldest session first — drives the per-user family cap. */
    @Query("SELECT t.familyId FROM RefreshToken t "
            + "WHERE t.userId = :userId AND t.revokedAt IS NULL AND t.expiresAt > :now "
            + "GROUP BY t.familyId ORDER BY MIN(t.issuedAt) ASC")
    List<String> findLiveFamilyIds(@Param("userId") String userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    /**
     * Deletes rows revoked before {@code cutoff}.
     *
     * <p>The retention window this cutoff implements is not housekeeping slack. Reuse detection
     * works by finding a replayed token's row still marked {@code ROTATED}; delete revoked rows
     * promptly and a stolen token instead hashes to nothing, yielding a plain 401 with no family
     * revocation — the attack becomes invisible.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.revokedAt IS NOT NULL AND t.revokedAt < :cutoff")
    int deleteRevokedBefore(@Param("cutoff") Instant cutoff);
}
