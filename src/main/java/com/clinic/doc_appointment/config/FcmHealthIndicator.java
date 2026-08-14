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

    private Health.Builder baseStatus(long pending, long dead) {
        if (messaging == null) {
            // OUT_OF_SERVICE rather than DOWN: a developer machine with no credentials should not
            // fail its own readiness probe over a feature it was never given.
            return Health.outOfService().withDetail("reason", "no Firebase credentials configured");
        }
        if (dead > 0) {
            return Health.down().withDetail("reason", dead + " push notification(s) abandoned after retries");
        }
        if (pending > properties.getBacklogWarn()) {
            return Health.down().withDetail("reason", "push backlog of " + pending + " exceeds threshold");
        }
        return Health.up();
    }
}
