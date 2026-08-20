package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-IP failure counter with a temporary block once a threshold is reached.
 *
 * <p><strong>Counts failures, not throughput.</strong> A working client's requests succeed, and
 * success clears the counter; someone guessing produces nothing but failures. A throughput cap
 * would instead penalise the legitimate bursts these endpoints create.
 *
 * <p>State is per-instance (no shared store); adequate for a single-node deployment. Subclasses
 * own the scheduled sweep that bounds memory against IP rotation, because the sweep interval is
 * configured per endpoint.
 *
 * <h2>Why every field is volatile and every mutation goes through {@code compute}</h2>
 *
 * <p>The two limiters this replaces guarded reads with {@code synchronized (attempt)} while writing
 * inside {@code ConcurrentHashMap.compute}, which holds the map's bin lock — a <em>different</em>
 * monitor. The synchronized block therefore established no happens-before edge with the writer, and
 * {@code blockedUntil} was a plain {@code long}. A request could observe {@code blockedUntil == 0}
 * and slip past a block another thread had just installed. Reads now go through volatile fields, so
 * the visibility guarantee is real rather than apparent, and the misleading lock is gone.
 */
@Slf4j
public abstract class AbstractAttemptLimiter {

    private final int maxAttempts;
    private final long blockMillis;
    private final String blockedMessageTemplate;
    private final String logSubject;

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * @param maxAttempts            consecutive failures tolerated before a block is installed
     * @param blockMinutes           how long a block lasts
     * @param blockedMessageTemplate message with a single {@code %d} placeholder for minutes remaining
     * @param logSubject             short phrase naming the guarded action, for the WARN line
     */
    protected AbstractAttemptLimiter(int maxAttempts, long blockMinutes,
                                     String blockedMessageTemplate, String logSubject) {
        this.maxAttempts = maxAttempts;
        this.blockMillis = blockMinutes * 60_000L;
        this.blockedMessageTemplate = blockedMessageTemplate;
        this.logSubject = logSubject;
    }

    private static final class Attempt {
        volatile int count;
        volatile long blockedUntil;      // epoch millis; 0 = not blocked
        volatile long lastActivity;      // epoch millis
    }

    /** Rejects the request if the IP is currently blocked. */
    public void assertNotBlocked(String ip) {
        Attempt attempt = attempts.get(ip);
        if (attempt == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long blockedUntil = attempt.blockedUntil;
        if (blockedUntil > now) {
            long minutesLeft = Math.max(1, (blockedUntil - now + 59_999L) / 60_000L);
            throw new TooManyRequestsException(String.format(blockedMessageTemplate, minutesLeft));
        }
    }

    /** Records a failed attempt; blocks the IP once the threshold is reached. */
    public void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        attempts.compute(ip, (key, existing) -> {
            Attempt attempt = existing != null ? existing : new Attempt();
            // If a previous block has fully elapsed, start a fresh count.
            if (attempt.blockedUntil != 0 && attempt.blockedUntil <= now) {
                attempt.count = 0;
                attempt.blockedUntil = 0;
            }
            attempt.count++;
            attempt.lastActivity = now;
            if (attempt.count >= maxAttempts && attempt.blockedUntil <= now) {
                attempt.blockedUntil = now + blockMillis;
                log.warn("IP {} blocked for {} after {} failed attempts", key, logSubject, attempt.count);
            }
            return attempt;
        });
    }

    /** Clears an IP's state after a successful attempt. */
    public void recordSuccess(String ip) {
        attempts.remove(ip);
    }

    /**
     * Evicts entries that are no longer blocked and have been idle for at least one block period.
     *
     * <p>Called from the subclass's {@code @Scheduled} method, whose interval is endpoint-specific.
     *
     * <p>Uses {@code computeIfPresent} (returning {@code null} to remove) rather than
     * {@code values().removeIf}. That matters: {@code removeIf} evaluates its predicate <em>outside</em>
     * the bin lock and then compare-removes on the value's identity — and since {@code Attempt} is
     * mutated in place, that identity check still matches after a concurrent
     * {@link #recordFailure} has installed a block, so the block would be evicted moments after being
     * set. {@code computeIfPresent} evaluates the decision while holding the same lock the writer
     * uses, which closes that window.
     */
    protected void evictStaleEntries() {
        long now = System.currentTimeMillis();
        for (String ip : attempts.keySet()) {
            attempts.computeIfPresent(ip, (key, attempt) -> {
                boolean notBlocked = attempt.blockedUntil <= now;
                boolean idle = now - attempt.lastActivity > blockMillis;
                return (notBlocked && idle) ? null : attempt;
            });
        }
    }
}
