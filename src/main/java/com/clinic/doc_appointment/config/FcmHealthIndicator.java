package com.clinic.doc_appointment.config;

import com.clinic.doc_appointment.enums.PushOutboxStatus;
import com.clinic.doc_appointment.repository.PushOutboxRepository;
import com.clinic.doc_appointment.service.push.PushProperties;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Reports push-delivery health at {@code /actuator/health}.
 *
 * <p>Derives entirely from durable state — whether a {@link FirebaseMessaging} bean exists, plus
 * counts from {@code push_outbox} — rather than from an in-memory status object. That means the
 * signal survives a restart and cannot drift from what actually happened.
 */
@Component("fcmPush")
public class FcmHealthIndicator implements HealthIndicator {

    @Nullable
    private final FirebaseMessaging messaging;
    private final PushOutboxRepository outboxRepository;
    private final PushProperties properties;

    public FcmHealthIndicator(@Nullable FirebaseMessaging messaging,
                              PushOutboxRepository outboxRepository,
                              PushProperties properties) {
        this.messaging = messaging;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
    }

    @Override
    public Health health() {
        long pending = outboxRepository.countByStatus(PushOutboxStatus.PENDING);
        long dead = outboxRepository.countByStatus(PushOutboxStatus.DEAD);
        Instant oldestDue = outboxRepository.findOldestDueDeadline(PushOutboxStatus.CLAIMABLE);

        Health.Builder builder = baseStatus(pending, dead);
        return builder
                .withDetail("provider", messaging == null ? "NOT_CONFIGURED" : "FIREBASE")
                .withDetail("workerEnabled", properties.isEnabled())
                .withDetail("pending", pending)
                .withDetail("dead", dead)
                .withDetail("oldestDue", oldestDue == null ? "none" : oldestDue.toString())
                .build();
    }

    /**
     * Always reports UP, adding {@code pushDegraded} plus a reason when something is wrong.
     *
     * <p>This indicator is aggregated into {@code /actuator/health}, which is the natural readiness
     * probe and maps DOWN to HTTP 503. Reporting DOWN here therefore removed the instance from the
     * load balancer — and because DEAD rows are retained for {@code retain-dead-days} (30 by design,
     * so an abandoned push stays visible), a single uninstalled app would have kept the service out
     * of rotation for a month. {@code OUT_OF_SERVICE} was no better: it is also 503, which
     * contradicted the intent of not failing a developer machine that simply has no credentials.
     *
     * <p>The details below are the alerting signal, together with the periodic WARN in
     * {@code PushOutboxWorker.reportBacklog}. Ops can alert on {@code pushDegraded} without the
     * probe killing a process whose HTTP surface is perfectly healthy.
     */
    private Health.Builder baseStatus(long pending, long dead) {
        if (messaging == null) {
            return degraded("no Firebase credentials configured");
        }
        if (dead > 0) {
            return degraded(dead + " push notification(s) abandoned after retries");
        }
        if (pending > properties.getBacklogWarn()) {
            return degraded("push backlog of " + pending + " exceeds threshold");
        }
        return Health.up().withDetail("pushDegraded", false);
    }

    private Health.Builder degraded(String reason) {
        return Health.up()
                .withDetail("pushDegraded", true)
                .withDetail("reason", reason);
    }
}
