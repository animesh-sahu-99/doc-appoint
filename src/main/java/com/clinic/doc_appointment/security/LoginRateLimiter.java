package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-IP brute-force guard for login. Counts consecutive failed
 * authentications from an IP; once {@code max-attempts} is reached the IP is blocked
 * for {@code block-minutes}. A successful authentication clears the IP's state.
 *
 * <p>State is per-instance (no shared store); adequate for a single-node deployment.
 * A scheduled sweep evicts stale entries to bound memory against IP rotation.
 */
@Component
@Slf4j
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long blockMillis;

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${ratelimit.login.max-attempts:5}") int maxAttempts,
            @Value("${ratelimit.login.block-minutes:15}") long blockMinutes) {
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
                        "Too many failed login attempts. Please try again in "
                                + minutesLeft + " minute(s).");
            }
        }
    }

    /** Records a failed authentication; blocks the IP once the threshold is reached. */
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
                log.warn("IP {} blocked for login after {} failed attempts", key, a.count);
            }
            return a;
        });
    }

    /** Clears an IP's state after a successful authentication. */
    public void recordSuccess(String ip) {
        attempts.remove(ip);
    }

    /** Periodically evict entries that are no longer blocked and have been idle. */
    @Scheduled(fixedDelayString = "${ratelimit.login.cleanup-millis:600000}")
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
