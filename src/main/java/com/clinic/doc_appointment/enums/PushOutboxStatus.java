package com.clinic.doc_appointment.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of one queued push notification.
 *
 * <p>Persisted as a string ({@code @Enumerated(EnumType.STRING)}), so these names are part of the
 * stored data — renaming one orphans existing rows.
 *
 * <p>The two sets below declare the state machine once, so every query and every worker branch
 * agrees on what "claimable" and "finished" mean.
 *
 * <pre>
 *   PENDING ──claim──&gt; IN_FLIGHT ──success──&gt; SENT
 *      ^                    │
 *      └───scheduleRetry────┤──no tokens────&gt; SKIPPED
 *                           └──gave up──────&gt; DEAD
 * </pre>
 */
public enum PushOutboxStatus {

    /** Enqueued, or scheduled for another attempt. Due once {@code nextAttemptAt <= now}. */
    PENDING,

    /**
     * Leased by a worker. Becomes claimable again once {@code nextAttemptAt} — which doubles as
     * the lease deadline — passes, which is how a crashed worker's rows recover themselves.
     */
    IN_FLIGHT,

    /** At least one device accepted the push. */
    SENT,

    /** Retries exhausted, or every token was permanently dead. {@code lastError} is populated. */
    DEAD,

    /**
     * Nothing to deliver to — the user has no active device, or push is not configured on this
     * instance. Terminal and <em>expected</em>.
     *
     * <p>This exists so {@code count(DEAD)} stays a clean alert signal: a web-only user and a dev
     * box without Firebase credentials are not delivery failures, and lumping them into DEAD
     * would bury the rows that actually need attention.
     */
    SKIPPED;

    /** States a worker may lease. IN_FLIGHT is included so an expired lease is recoverable. */
    public static final Set<PushOutboxStatus> CLAIMABLE =
            Collections.unmodifiableSet(EnumSet.of(PENDING, IN_FLIGHT));

    /** Terminal states that represent "nothing went wrong" — swept sooner than DEAD. */
    public static final Set<PushOutboxStatus> COMPLETED =
            Collections.unmodifiableSet(EnumSet.of(SENT, SKIPPED));
}
