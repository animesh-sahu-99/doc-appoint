package com.clinic.doc_appointment.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-IP brute-force guard for login. Counts consecutive failed authentications from an IP; once
 * {@code max-attempts} is reached the IP is blocked for {@code block-minutes}. A successful
 * authentication clears the IP's state.
 *
 * <p>All of the mechanics live in {@link AbstractAttemptLimiter} — see that class for why the
 * counters are volatile. This subclass supplies only the login-specific limits, message and sweep
 * interval.
 */
@Component
public class LoginRateLimiter extends AbstractAttemptLimiter {

    public LoginRateLimiter(
            @Value("${ratelimit.login.max-attempts:5}") int maxAttempts,
            @Value("${ratelimit.login.block-minutes:15}") long blockMinutes) {
        super(maxAttempts, blockMinutes,
                "Too many failed login attempts. Please try again in %d minute(s).",
                "login");
    }

    /** Periodically evict entries that are no longer blocked and have been idle. */
    @Scheduled(fixedDelayString = "${ratelimit.login.cleanup-millis:600000}")
    void evictStale() {
        evictStaleEntries();
    }
}
