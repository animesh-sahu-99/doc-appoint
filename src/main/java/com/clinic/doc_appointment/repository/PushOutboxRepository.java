package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.PushOutboxMessage;
import com.clinic.doc_appointment.enums.PushOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for the push outbox.
 *
 * <p><strong>{@code @Transactional} lives here, not on the worker, and that is structural rather
 * than stylistic.</strong> Because every state change is a transactional repository call, the
 * worker itself can carry no transaction at all — which is what guarantees the blocking FCM call
 * happens with no database connection held. Moving these annotations onto the worker would also
 * break under self-invocation, since a private method call bypasses the proxy entirely.
 */
@Repository
public interface PushOutboxRepository extends JpaRepository<PushOutboxMessage, String> {

    Optional<PushOutboxMessage> findByNotificationId(String notificationId);

    /**
     * Rows that are due now, oldest deadline first.
     *
     * <p>Advisory only: another instance may claim any of these before we do, which {@link #claim}
     * detects. Full rows are returned so the worker needs no second read after winning a claim.
     */
    @Query("SELECT m FROM PushOutboxMessage m "
            + "WHERE m.status IN :claimable AND m.nextAttemptAt <= :now "
            + "ORDER BY m.nextAttemptAt ASC")
    List<PushOutboxMessage> findDue(@Param("claimable") Collection<PushOutboxStatus> claimable,
                                    @Param("now") Instant now,
                                    Pageable pageable);

    /**
     * Atomically leases one due row. Returns 1 when this instance won, 0 when another instance
     * already took it or it stopped being due.
     *
     * <p>Deliberately the same compare-and-set shape as
     * {@link RefreshTokenRepository#consumeIfLive}, for the same reason: under {@code READ
     * COMMITTED} two concurrent updates to one row serialize, and the loser re-evaluates the
     * predicate against the committed row and matches nothing. A read-then-write would leave a
     * window where both instances believe they won and the push is delivered twice.
     *
     * <p>{@code nextAttemptAt = :leaseUntil} is the crash-recovery mechanism: the lease deadline
     * and the retry deadline are the same column, so a worker that dies mid-send simply leaves a
     * row that becomes due again. No reaper, no separate recovery path.
     *
     * <p>{@code attempts} increments here, before the send, so a row that kills its worker every
     * time still walks to DEAD rather than being redelivered forever.
     *
     * <p>{@code clearAutomatically} is load-bearing: bulk JPQL bypasses the persistence context,
     * so without it a read after this call could return the stale, pre-update entity.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE PushOutboxMessage m "
            + "SET m.status = :inFlight, m.attempts = m.attempts + 1, "
            + "    m.claimedAt = :now, m.claimedBy = :worker, "
            + "    m.nextAttemptAt = :leaseUntil, m.updatedAt = :now "
            + "WHERE m.id = :id AND m.status IN :claimable AND m.nextAttemptAt <= :now")
    int claim(@Param("id") String id,
              @Param("worker") String worker,
              @Param("inFlight") PushOutboxStatus inFlight,
              @Param("claimable") Collection<PushOutboxStatus> claimable,
              @Param("now") Instant now,
              @Param("leaseUntil") Instant leaseUntil);

    /**
     * Moves a leased row to a terminal state (SENT / DEAD / SKIPPED).
     *
     * <p>The {@code claimedBy} predicate is not decoration. If our lease expired mid-send and
     * another worker re-claimed the row, this must not clobber their in-flight state. A 0 return
     * means exactly that, and is the signal that the lease window is too short to log and ignore.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE PushOutboxMessage m "
            + "SET m.status = :terminal, m.lastError = :lastError, "
            + "    m.claimedAt = null, m.claimedBy = null, m.updatedAt = :now "
            + "WHERE m.id = :id AND m.status = :inFlight AND m.claimedBy = :worker")
    int finish(@Param("id") String id,
               @Param("worker") String worker,
               @Param("inFlight") PushOutboxStatus inFlight,
               @Param("terminal") PushOutboxStatus terminal,
               @Param("lastError") String lastError,
               @Param("now") Instant now);

    /** Releases the lease and schedules the next attempt. Same stolen-lease guard as {@link #finish}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE PushOutboxMessage m "
            + "SET m.status = :pending, m.nextAttemptAt = :nextAttemptAt, m.lastError = :lastError, "
            + "    m.claimedAt = null, m.claimedBy = null, m.updatedAt = :now "
            + "WHERE m.id = :id AND m.status = :inFlight AND m.claimedBy = :worker")
    int scheduleRetry(@Param("id") String id,
                      @Param("worker") String worker,
                      @Param("inFlight") PushOutboxStatus inFlight,
                      @Param("pending") PushOutboxStatus pending,
                      @Param("nextAttemptAt") Instant nextAttemptAt,
                      @Param("lastError") String lastError,
                      @Param("now") Instant now);

    /**
     * Deletes terminal rows older than {@code cutoff}.
     *
     * <p>Callers sweep completed rows promptly but DEAD rows slowly. That asymmetry is not
     * housekeeping slack: a DEAD row plus its {@code lastError} is the only record that a push was
     * ever abandoned, so deleting them quickly makes the failure invisible.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PushOutboxMessage m WHERE m.status IN :statuses AND m.updatedAt < :cutoff")
    int deleteTerminalBefore(@Param("statuses") Collection<PushOutboxStatus> statuses,
                             @Param("cutoff") Instant cutoff);

    // ===================== observability =====================

    long countByStatus(PushOutboxStatus status);

    /** Oldest deadline still awaiting delivery — the backlog age signal. */
    @Query("SELECT MIN(m.nextAttemptAt) FROM PushOutboxMessage m WHERE m.status IN :claimable")
    Instant findOldestDueDeadline(@Param("claimable") Collection<PushOutboxStatus> claimable);
}
