package com.clinic.doc_appointment.service.push;

import com.clinic.doc_appointment.entity.PushOutboxMessage;
import com.clinic.doc_appointment.entity.UserDevice;
import com.clinic.doc_appointment.enums.PushOutboxStatus;
import com.clinic.doc_appointment.repository.PushOutboxRepository;
import com.clinic.doc_appointment.repository.UserDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Delivers queued push notifications, off the request thread and with retries.
 *
 * <p>Each row goes through three phases with deliberately different transaction boundaries:
 * <strong>claim</strong> (transactional), <strong>send</strong> (no transaction — this is blocking
 * network I/O), <strong>record</strong> (transactional). That split is the whole point: no
 * database connection is ever held across a call to Google.
 *
 * <p><strong>This class carries no {@code @Transactional} anywhere, on purpose.</strong> Every
 * state change goes through {@link PushOutboxRepository}, whose methods are transactional
 * individually. Moving those annotations here would put the FCM call inside a transaction and
 * would additionally break under self-invocation, since a private call bypasses the proxy.
 */
@Component
@Slf4j
public class PushOutboxWorker {

    private static final String WORKER_ID = resolveWorkerId();

    private final PushOutboxRepository outboxRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PushNotificationProvider pushProvider;
    private final PushProperties properties;

    public PushOutboxWorker(PushOutboxRepository outboxRepository,
                            UserDeviceRepository userDeviceRepository,
                            PushNotificationProvider pushProvider,
                            PushProperties properties) {
        this.outboxRepository = outboxRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.pushProvider = pushProvider;
        this.properties = properties;
    }

    /**
     * Claims and delivers every due row, up to the batch size.
     *
     * <p>Package-private so tests drive it directly rather than waiting on the scheduler — the
     * same approach {@code LoginRateLimiter.evictStale} takes.
     */
    @Scheduled(fixedDelayString = "${push.outbox.poll-millis:1000}")
    void drainDue() {
        if (!properties.isEnabled()) {
            return;
        }

        List<PushOutboxMessage> due = outboxRepository.findDue(
                PushOutboxStatus.CLAIMABLE, Instant.now(), PageRequest.of(0, properties.getBatchSize()));

        if (due.isEmpty()) {
            return;   // deliberately silent: this runs every second
        }

        for (PushOutboxMessage row : due) {
            try {
                deliver(row);
            } catch (RuntimeException e) {
                // One poisoned row must never abort the rest of the batch.
                log.error("Unhandled error delivering push outbox row {} (notification {})",
                        row.getId(), row.getNotificationId(), e);
            }
        }
    }

    /**
     * Claims one row, sends it, records the outcome.
     *
     * <p>The clock is read here, per row, rather than once for the whole batch. Deriving the lease
     * from the start of the drain meant row <em>i</em> was claimed with an effective lease of
     * {@code lease-millis} minus however long rows 0..i-1 took — which goes negative once a couple
     * of sends run slow, handing out a claim that has already expired and is immediately
     * re-claimable. That is a duplicate push to the user, not just skewed bookkeeping.
     */
    private void deliver(PushOutboxMessage row) {
        Instant now = Instant.now();
        Instant leaseUntil = now.plusMillis(properties.getLeaseMillis());

        if (outboxRepository.claim(row.getId(), WORKER_ID, PushOutboxStatus.IN_FLIGHT,
                PushOutboxStatus.CLAIMABLE, now, leaseUntil) == 0) {
            // Another instance took it, or it stopped being due. Not an error.
            return;
        }
        // We know exactly what the claim wrote, so no re-read is needed.
        int attempts = row.getAttempts() + 1;

        List<String> tokens = userDeviceRepository.findByUserIdAndIsActiveTrue(row.getUserId())
                .stream()
                .map(UserDevice::getFcmToken)
                .toList();

        if (tokens.isEmpty()) {
            finish(row, PushOutboxStatus.SKIPPED, "no active device tokens");
            return;
        }

        // ── No transaction is open across this call. ────────────────────────────
        PushSendReport report = pushProvider.send(tokens,
                new PushMessage(row.getTitle(), row.getBody(), row.getType().name(), row.getRelatedEntityId()));

        applyOutcome(row, report, attempts);
    }

    private void applyOutcome(PushOutboxMessage row, PushSendReport report, int attempts) {
        List<String> deadTokens = report.deadTokens();
        if (!deadTokens.isEmpty()) {
            int deactivated = userDeviceRepository.deactivateTokens(deadTokens, LocalDateTime.now());
            log.warn("Deactivated {} dead device token(s) for user {}", deactivated, row.getUserId());
        }

        Decision decision = decide(report, attempts, properties.getMaxAttempts());

        switch (decision) {
            case SENT -> finish(row, PushOutboxStatus.SENT, null);
            case SKIPPED -> finish(row, PushOutboxStatus.SKIPPED, "push provider not configured");
            case DEAD -> {
                String error = firstError(report);
                log.error("Push outbox DEAD: notification={} user={} attempts={} lastError={}",
                        row.getNotificationId(), row.getUserId(), attempts, error);
                finish(row, PushOutboxStatus.DEAD, error);
            }
            case RETRY -> scheduleRetry(row, attempts, firstError(report), true);
            // Credentials are broken, not the message. Retrying is harmless but pointless until a
            // human fixes it, so back off long WITHOUT consuming the attempt budget — otherwise a
            // weekend of expired credentials would dead-letter the entire queue.
            case RETRY_WITHOUT_PENALTY -> scheduleRetry(row, attempts, firstError(report), false);
        }
    }

    /** What to do with a row given its send report. Pure, so it can be tested without mocks. */
    enum Decision { SENT, SKIPPED, DEAD, RETRY, RETRY_WITHOUT_PENALTY }

    static Decision decide(PushSendReport report, int attempts, int maxAttempts) {
        if (report.providerUnavailable()) {
            return Decision.SKIPPED;
        }
        if (report.deliveredCount() > 0) {
            return Decision.SENT;
        }
        if (!report.anyRetryable()) {
            // Every recipient failed permanently — a dead token or a rejected payload.
            return Decision.DEAD;
        }
        if (report.results().stream().anyMatch(r -> r.outcome() == PushOutcome.PROVIDER_UNCONFIGURED)) {
            return Decision.RETRY_WITHOUT_PENALTY;
        }
        return attempts >= maxAttempts ? Decision.DEAD : Decision.RETRY;
    }

    private void finish(PushOutboxMessage row, PushOutboxStatus terminal, String error) {
        int updated = outboxRepository.finish(row.getId(), WORKER_ID, PushOutboxStatus.IN_FLIGHT,
                terminal, PushOutboxMessage.truncateError(error), Instant.now());
        warnIfLeaseLost(updated, row);
    }

    private void scheduleRetry(PushOutboxMessage row, int attempts, String error, boolean penalise) {
        Instant now = Instant.now();
        Instant next = penalise
                ? nextAttempt(attempts, properties.getBackoffMillis(), properties.getBackoffMultiplier(),
                        properties.getMaxBackoffMillis(), now)
                : now.plusMillis(properties.getMaxBackoffMillis());

        log.warn("Push retry {}/{} for notification {} at {} — {}",
                attempts, properties.getMaxAttempts(), row.getNotificationId(), next, error);

        int updated = outboxRepository.scheduleRetry(row.getId(), WORKER_ID, PushOutboxStatus.IN_FLIGHT,
                PushOutboxStatus.PENDING, next, PushOutboxMessage.truncateError(error), now);
        warnIfLeaseLost(updated, row);
    }

    /**
     * A zero row count means our lease expired mid-send and another worker re-claimed the row.
     * Harmless in itself — the other worker owns it now — but it is the signal that
     * {@code lease-millis} is too short for how long sends actually take.
     */
    private void warnIfLeaseLost(int updated, PushOutboxMessage row) {
        if (updated == 0) {
            log.warn("Lease lost mid-send for outbox row {} (notification {}) — another worker "
                    + "re-claimed it. Consider raising push.outbox.lease-millis.",
                    row.getId(), row.getNotificationId());
        }
    }

    /**
     * Exponential backoff with ±20% jitter, capped.
     *
     * <p>Jitter matters here: without it, every row queued during an FCM outage becomes due at the
     * same instant when it recovers, and the herd re-fails together.
     */
    static Instant nextAttempt(int attempts, long baseMillis, double multiplier,
                               long maxMillis, Instant now) {
        double raw = baseMillis * Math.pow(multiplier, Math.max(0, attempts - 1));
        long jittered = (long) (raw * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4));
        // Cap AFTER jitter, so max-backoff-millis is a genuine ceiling. Capping first would let
        // the +20% jitter push the delay past the configured maximum.
        long capped = Math.min(jittered, maxMillis);
        return now.plusMillis(Math.max(1, capped));
    }

    private String firstError(PushSendReport report) {
        return report.results().stream()
                .filter(r -> !r.isDelivered())
                .map(r -> r.errorCode() + ": " + r.errorDetail())
                .findFirst()
                .orElse(null);
    }

    /**
     * Removes finished rows: completed ones promptly, dead ones slowly.
     *
     * <p>Runs on every instance; the deletes are idempotent so that is harmless — the same
     * property {@code RefreshTokenService.purgeExpiredAndStaleRevoked} relies on.
     *
     * <p>The long DEAD retention is not housekeeping slack. A dead row plus its {@code lastError}
     * is the only record that a push was ever abandoned; sweeping those quickly would make the
     * failure invisible.
     */
    @Scheduled(fixedDelayString = "${push.outbox.cleanup-millis:3600000}")
    void purgeTerminal() {
        Instant now = Instant.now();
        int completed = outboxRepository.deleteTerminalBefore(PushOutboxStatus.COMPLETED,
                now.minus(properties.getRetainSentDays(), ChronoUnit.DAYS));
        int dead = outboxRepository.deleteTerminalBefore(Set.of(PushOutboxStatus.DEAD),
                now.minus(properties.getRetainDeadDays(), ChronoUnit.DAYS));
        if (completed + dead > 0) {
            log.info("Push outbox sweep: removed {} completed and {} dead row(s)", completed, dead);
        }
    }

    /** Periodic backlog line — the one thing an operator can grep for to see push health. */
    @Scheduled(fixedDelayString = "${push.outbox.report-millis:300000}")
    void reportBacklog() {
        long pending = outboxRepository.countByStatus(PushOutboxStatus.PENDING);
        long inFlight = outboxRepository.countByStatus(PushOutboxStatus.IN_FLIGHT);
        long dead = outboxRepository.countByStatus(PushOutboxStatus.DEAD);
        Instant oldest = outboxRepository.findOldestDueDeadline(PushOutboxStatus.CLAIMABLE);

        if (pending > properties.getBacklogWarn() || dead > 0) {
            log.warn("Push outbox: pending={} inFlight={} dead={} oldestDue={}", pending, inFlight, dead, oldest);
        } else {
            log.debug("Push outbox: pending={} inFlight={} dead={} oldestDue={}", pending, inFlight, dead, oldest);
        }
    }

    /** "pid@host" — forensic context on a claimed row, and the guard for the outcome write. */
    private static String resolveWorkerId() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.length() <= 64 ? name : name.substring(0, 64);
    }
}
