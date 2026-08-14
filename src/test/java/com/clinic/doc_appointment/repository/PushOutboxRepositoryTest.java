package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.PushOutboxMessage;
import com.clinic.doc_appointment.enums.NotificationType;
import com.clinic.doc_appointment.enums.PushOutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the outbox against a real (embedded) database.
 *
 * <p>As with {@code RefreshTokenRepositoryTest}, the point is less the assertions than the fact
 * that this boots: it proves the JPQL parses — enum-typed {@code IN} parameters and a
 * {@code Pageable} on an {@code @Query} are both non-obvious — and that the {@code push_outbox}
 * mapping is valid. Neither is catchable by a mock, and both would otherwise surface only at
 * application startup.
 */
@DataJpaTest
class PushOutboxRepositoryTest {

    private static final String WORKER = "worker-1";
    private static final String OTHER_WORKER = "worker-2";

    @Autowired
    private PushOutboxRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private PushOutboxMessage persist(String notificationId, PushOutboxStatus status,
                                      Instant nextAttemptAt, String claimedBy) {
        Instant now = Instant.now();
        return entityManager.persistAndFlush(PushOutboxMessage.builder()
                .notificationId(notificationId)
                .userId("PAT-1")
                .title("Appointment Confirmed")
                .body("Your appointment is confirmed.")
                .type(NotificationType.APPOINTMENT_UPDATE)
                .relatedEntityId("APPOINTMENT-1")
                .status(status)
                .attempts(0)
                .nextAttemptAt(nextAttemptAt)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private PushOutboxMessage pendingNow(String notificationId) {
        return persist(notificationId, PushOutboxStatus.PENDING, Instant.now().minusSeconds(1), null);
    }

    private int claim(String id, String worker, Instant now) {
        return repository.claim(id, worker, PushOutboxStatus.IN_FLIGHT,
                PushOutboxStatus.CLAIMABLE, now, now.plusSeconds(120));
    }

    // ===================== mapping =====================

    @Test
    void persistsAndReadsBack() {
        PushOutboxMessage saved = pendingNow("NOT-1");
        entityManager.clear();

        PushOutboxMessage found = repository.findById(saved.getId()).orElseThrow();
        assertNotNull(found.getId(), "UUID generator must assign an id");
        assertEquals("NOT-1", found.getNotificationId());
        assertEquals(PushOutboxStatus.PENDING, found.getStatus());
        assertEquals(NotificationType.APPOINTMENT_UPDATE, found.getType());
    }

    @Test
    void enqueueIsIdempotentPerNotification() {
        pendingNow("NOT-dup");

        // The unique constraint is what stops a retried enqueue double-sending.
        assertThrows(Exception.class, () -> {
            pendingNow("NOT-dup");
            entityManager.flush();
        });
    }

    // ===================== findDue =====================

    @Test
    void findDueReturnsOnlyClaimableRowsThatAreActuallyDue() {
        pendingNow("NOT-due");
        persist("NOT-future", PushOutboxStatus.PENDING, Instant.now().plus(1, ChronoUnit.HOURS), null);
        persist("NOT-sent", PushOutboxStatus.SENT, Instant.now().minusSeconds(60), null);
        entityManager.clear();

        List<PushOutboxMessage> due = repository.findDue(
                PushOutboxStatus.CLAIMABLE, Instant.now(), PageRequest.of(0, 10));

        assertEquals(1, due.size());
        assertEquals("NOT-due", due.get(0).getNotificationId());
    }

    @Test
    void findDueReturnsOldestFirstAndHonoursTheLimit() {
        persist("NOT-old", PushOutboxStatus.PENDING, Instant.now().minus(10, ChronoUnit.MINUTES), null);
        persist("NOT-mid", PushOutboxStatus.PENDING, Instant.now().minus(5, ChronoUnit.MINUTES), null);
        persist("NOT-new", PushOutboxStatus.PENDING, Instant.now().minusSeconds(1), null);
        entityManager.clear();

        List<PushOutboxMessage> due = repository.findDue(
                PushOutboxStatus.CLAIMABLE, Instant.now(), PageRequest.of(0, 2));

        assertEquals(2, due.size());
        assertEquals("NOT-old", due.get(0).getNotificationId());
        assertEquals("NOT-mid", due.get(1).getNotificationId());
    }

    // ===================== claim: the concurrency contract =====================

    @Test
    void claimSucceedsOnceAndOnlyOnce() {
        PushOutboxMessage row = pendingNow("NOT-race");
        entityManager.clear();
        Instant now = Instant.now();

        assertEquals(1, claim(row.getId(), WORKER, now), "first worker wins");
        assertEquals(0, claim(row.getId(), OTHER_WORKER, now),
                "a second instance must not also win — this is what prevents a double send");
    }

    @Test
    void claimMarksTheRowInFlightAndIncrementsAttempts() {
        PushOutboxMessage row = pendingNow("NOT-claim");
        entityManager.clear();

        claim(row.getId(), WORKER, Instant.now());

        // Also proves @Modifying(clearAutomatically = true): without it this read would return
        // the stale pre-update entity from the persistence context.
        PushOutboxMessage after = repository.findById(row.getId()).orElseThrow();
        assertEquals(PushOutboxStatus.IN_FLIGHT, after.getStatus());
        assertEquals(1, after.getAttempts());
        assertEquals(WORKER, after.getClaimedBy());
        assertNotNull(after.getClaimedAt());
    }

    @Test
    void claimRefusesARowThatIsNotYetDue() {
        PushOutboxMessage row = persist("NOT-later", PushOutboxStatus.PENDING,
                Instant.now().plus(1, ChronoUnit.HOURS), null);
        entityManager.clear();

        assertEquals(0, claim(row.getId(), WORKER, Instant.now()));
    }

    /** Crash recovery: the lease deadline and the retry deadline are the same column. */
    @Test
    void claimReclaimsAnInFlightRowWhoseLeaseExpired() {
        PushOutboxMessage abandoned = persist("NOT-abandoned", PushOutboxStatus.IN_FLIGHT,
                Instant.now().minus(5, ChronoUnit.MINUTES), "dead-worker");
        entityManager.clear();

        assertEquals(1, claim(abandoned.getId(), WORKER, Instant.now()),
                "a worker that died mid-send must not strand its rows forever");
    }

    // ===================== outcome writes and the stolen-lease guard =====================

    @Test
    void finishMovesAClaimedRowToItsTerminalState() {
        PushOutboxMessage row = pendingNow("NOT-finish");
        entityManager.clear();
        Instant now = Instant.now();
        claim(row.getId(), WORKER, now);

        int updated = repository.finish(row.getId(), WORKER, PushOutboxStatus.IN_FLIGHT,
                PushOutboxStatus.SENT, null, now);

        assertEquals(1, updated);
        PushOutboxMessage after = repository.findById(row.getId()).orElseThrow();
        assertEquals(PushOutboxStatus.SENT, after.getStatus());
        assertNull(after.getClaimedBy(), "the lease must be released");
    }

    @Test
    void finishRefusesToClobberAnotherWorkersLease() {
        PushOutboxMessage row = pendingNow("NOT-stolen");
        entityManager.clear();
        Instant now = Instant.now();
        claim(row.getId(), WORKER, now);

        int updated = repository.finish(row.getId(), OTHER_WORKER, PushOutboxStatus.IN_FLIGHT,
                PushOutboxStatus.SENT, null, now);

        assertEquals(0, updated, "a worker whose lease expired must not overwrite the new holder");
    }

    @Test
    void scheduleRetryReleasesTheLeaseAndPushesTheDeadlineOut() {
        PushOutboxMessage row = pendingNow("NOT-retry");
        entityManager.clear();
        Instant now = Instant.now();
        claim(row.getId(), WORKER, now);
        Instant next = now.plus(15, ChronoUnit.MINUTES);

        int updated = repository.scheduleRetry(row.getId(), WORKER, PushOutboxStatus.IN_FLIGHT,
                PushOutboxStatus.PENDING, next, "UNAVAILABLE: fcm down", now);

        assertEquals(1, updated);
        PushOutboxMessage after = repository.findById(row.getId()).orElseThrow();
        assertEquals(PushOutboxStatus.PENDING, after.getStatus());
        assertNull(after.getClaimedBy());
        assertTrue(after.getNextAttemptAt().isAfter(now));
        assertEquals("UNAVAILABLE: fcm down", after.getLastError());
    }

    @Test
    void scheduleRetryRefusesToClobberAnotherWorkersLease() {
        PushOutboxMessage row = pendingNow("NOT-retry-stolen");
        entityManager.clear();
        Instant now = Instant.now();
        claim(row.getId(), WORKER, now);

        assertEquals(0, repository.scheduleRetry(row.getId(), OTHER_WORKER, PushOutboxStatus.IN_FLIGHT,
                PushOutboxStatus.PENDING, now.plusSeconds(60), null, now));
    }

    // ===================== sweep =====================

    @Test
    void sweepRemovesCompletedRowsButSparesDeadAndPending() {
        Instant old = Instant.now().minus(10, ChronoUnit.DAYS);
        entityManager.persistAndFlush(aged("NOT-sent", PushOutboxStatus.SENT, old));
        entityManager.persistAndFlush(aged("NOT-skipped", PushOutboxStatus.SKIPPED, old));
        entityManager.persistAndFlush(aged("NOT-dead", PushOutboxStatus.DEAD, old));
        pendingNow("NOT-live");
        entityManager.clear();

        int removed = repository.deleteTerminalBefore(
                PushOutboxStatus.COMPLETED, Instant.now().minus(3, ChronoUnit.DAYS));

        assertEquals(2, removed);
        // DEAD rows are the forensic record of an abandoned push — they must outlive the rest.
        assertTrue(repository.findByNotificationId("NOT-dead").isPresent());
        assertTrue(repository.findByNotificationId("NOT-live").isPresent());
    }

    @Test
    void sweepRemovesDeadRowsOnlyAfterTheirLongerRetention() {
        entityManager.persistAndFlush(aged("NOT-ancient", PushOutboxStatus.DEAD,
                Instant.now().minus(40, ChronoUnit.DAYS)));
        entityManager.clear();

        assertEquals(1, repository.deleteTerminalBefore(Set.of(PushOutboxStatus.DEAD),
                Instant.now().minus(30, ChronoUnit.DAYS)));
    }

    private PushOutboxMessage aged(String notificationId, PushOutboxStatus status, Instant updatedAt) {
        return PushOutboxMessage.builder()
                .notificationId(notificationId)
                .userId("PAT-1")
                .title("t")
                .body("b")
                .type(NotificationType.APPOINTMENT_UPDATE)
                .status(status)
                .attempts(1)
                .nextAttemptAt(updatedAt)
                .createdAt(updatedAt)
                .updatedAt(updatedAt)
                .build();
    }

    // ===================== observability queries =====================

    @Test
    void countsAndOldestDeadlineBackTheHealthReport() {
        pendingNow("NOT-a");
        pendingNow("NOT-b");
        entityManager.persistAndFlush(aged("NOT-c", PushOutboxStatus.DEAD, Instant.now()));
        entityManager.clear();

        assertEquals(2, repository.countByStatus(PushOutboxStatus.PENDING));
        assertEquals(1, repository.countByStatus(PushOutboxStatus.DEAD));
        assertNotNull(repository.findOldestDueDeadline(PushOutboxStatus.CLAIMABLE));
    }
}
