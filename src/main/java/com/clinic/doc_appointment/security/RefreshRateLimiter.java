package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-IP guard for the token refresh endpoint.
 *
 * <p><strong>Counts failures, not throughput.</strong> A working client's refreshes essentially
 * always succeed, and success clears the counter; someone guessing at 256-bit opaque tokens
 * produces nothing but failures. A throughput cap would instead penalise the legitimate burst
 * this feature creates — several requests expiring at once, each triggering a refresh.
 *
 * <p>Limits are deliberately looser than {@link LoginRateLimiter}'s. Mobile carriers put
 * thousands of subscribers behind one NAT address, and {@code RequestUtils.getClientIp} returns
 * the TCP peer unless {@code trust-forwarded-for} is enabled — so a tight per-IP block on a
 * carrier gateway would lock out unrelated users. Twenty failures remains nowhere near enough to
 * make brute force meaningful against that keyspace.
 *
 * <p>State is per-instance, as with {@link LoginRateLimiter}; adequate for a single-node
 * deployment. A scheduled sweep bounds memory against IP rotation.
 *
 * <p>Structurally a near-copy of {@link LoginRateLimiter}. Extracting a shared base is worth
 * doing, but its {@code @Scheduled} sweep is bound to a login-specific property key, so the
 * refactor is left as a follow-up rather than folded into an auth change.
 */
@Component
@Slf4j
public class RefreshRateLimiter {

    private final int maxAttempts;
    private final long blockMillis;

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public RefreshRateLimiter(
            @Value("${ratelimit.refresh.max-attempts:20}") int maxAttempts,
            @Value("${ratelimit.refresh.block-minutes:5}") long blockMinutes) {
        this.maxAttempts = maxAttempts;
        this.blockMillis = blockMinutes * 60_000L;
    }

    private static final class Attempt {
        int count;
        long blockedUntil;      // epoch millis; 0 = not blocked
        long lastActivity;      // epoch millis
    }

    /** Rejects the request if the IP is currently blocked. */
    public void assertNotBlocked(String ip) {
        Attempt a = attempts.get(ip);
        if (a == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (a) {
            if (a.blockedUntil > now) {
                long minutesLeft = Math.max(1, (a.blockedUntil - now + 59_999L) / 60_000L);
                throw new TooManyRequestsException(
                        "Too many refresh attempts. Please try again in "
                                + minutesLeft + " minute(s).");
            }
        }
    }

    /** Records a rejected refresh; blocks the IP once the threshold is reached. */
    public void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        attempts.compute(ip, (key, existing) -> {
            Attempt a = existing != null ? existing : new Attempt();
            // If a previous block has fully elapsed, start a fresh count.
            if (a.blockedUntil != 0 && a.blockedUntil <= now) {
                a.count = 0;
                a.blockedUntil = 0;
            }
            a.count++;
            a.lastActivity = now;
            if (a.count >= maxAttempts && a.blockedUntil <= now) {
                a.blockedUntil = now + blockMillis;
                log.warn("IP {} blocked for token refresh after {} failed attempts", key, a.count);
            }
            return a;
        });
    }

    /** Clears an IP's state after a successful refresh. */
    public void recordSuccess(String ip) {
        attempts.remove(ip);
    }

    /** Periodically evict entries that are no longer blocked and have been idle. */
    @Scheduled(fixedDelayString = "${ratelimit.refresh.cleanup-millis:600000}")
    void evictStale() {
        long now = System.currentTimeMillis();
        attempts.values().removeIf(a -> {
            synchronized (a) {
                boolean notBlocked = a.blockedUntil <= now;
                boolean idle = now - a.lastActivity > blockMillis;
                return notBlocked && idle;
            }
        });
    }
}
