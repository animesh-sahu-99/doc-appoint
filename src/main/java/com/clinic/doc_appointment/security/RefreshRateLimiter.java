package com.clinic.doc_appointment.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-IP guard for the token refresh endpoint.
 *
 * <p>Limits are deliberately looser than {@link LoginRateLimiter}'s. Mobile carriers put thousands
 * of subscribers behind one NAT address, and {@code RequestUtils.getClientIp} returns the TCP peer
 * unless {@code trust-forwarded-for} is enabled — so a tight per-IP block on a carrier gateway
 * would lock out unrelated users. Twenty failures remains nowhere near enough to make brute force
 * meaningful against a 256-bit opaque token.
 *
 * <p>Shares all mechanics with {@link LoginRateLimiter} through {@link AbstractAttemptLimiter}. The
 * two used to be a ~100-line copy-paste, which mattered as soon as the counters needed a
 * concurrency fix: it would have had to be made twice, correctly, in both.
 */
@Component
public class RefreshRateLimiter extends AbstractAttemptLimiter {

    public RefreshRateLimiter(
            @Value("${ratelimit.refresh.max-attempts:20}") int maxAttempts,
            @Value("${ratelimit.refresh.block-minutes:5}") long blockMinutes) {
        super(maxAttempts, blockMinutes,
                "Too many refresh attempts. Please try again in %d minute(s).",
                "token refresh");
    }

    /** Periodically evict entries that are no longer blocked and have been idle. */
    @Scheduled(fixedDelayString = "${ratelimit.refresh.cleanup-millis:600000}")
    void evictStale() {
        evictStaleEntries();
    }
}
