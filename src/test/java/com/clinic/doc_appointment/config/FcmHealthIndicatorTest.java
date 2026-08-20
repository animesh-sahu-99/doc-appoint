package com.clinic.doc_appointment.config;

import com.clinic.doc_appointment.enums.PushOutboxStatus;
import com.clinic.doc_appointment.repository.PushOutboxRepository;
import com.clinic.doc_appointment.service.push.PushProperties;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A degraded push pipeline must not report the application as DOWN.
 *
 * <p>{@code /actuator/health} is {@code permitAll} and is the natural readiness probe; DOWN maps to
 * HTTP 503. Because DEAD outbox rows are deliberately retained for 30 days so an abandoned push
 * stays visible, reporting DOWN on {@code dead > 0} took the instance out of the load balancer for a
 * month over a single uninstalled app. {@code OUT_OF_SERVICE} was no better — also 503.
 */
class FcmHealthIndicatorTest {

    private PushOutboxRepository outboxRepository;
    private PushProperties properties;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(PushOutboxRepository.class);
        properties = new PushProperties();
        properties.setBacklogWarn(100);
        when(outboxRepository.countByStatus(any())).thenReturn(0L);
        when(outboxRepository.findOldestDueDeadline(any())).thenReturn(null);
    }

    private Health health(FirebaseMessaging messaging) {
        return new FcmHealthIndicator(messaging, outboxRepository, properties).health();
    }

    @Test
    void reportsUpWhenEverythingIsQuiet() {
        Health health = health(mock(FirebaseMessaging.class));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("pushDegraded", false);
        assertThat(health.getDetails()).containsEntry("provider", "FIREBASE");
    }

    @Test
    void aDeadPushDegradesButDoesNotFailTheProbe() {
        when(outboxRepository.countByStatus(PushOutboxStatus.DEAD)).thenReturn(1L);

        Health health = health(mock(FirebaseMessaging.class));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("pushDegraded", true);
        assertThat(health.getDetails()).containsEntry("dead", 1L);
        assertThat(String.valueOf(health.getDetails().get("reason"))).contains("abandoned");
    }

    @Test
    void aBacklogDegradesButDoesNotFailTheProbe() {
        properties.setBacklogWarn(10);
        when(outboxRepository.countByStatus(PushOutboxStatus.PENDING)).thenReturn(500L);

        Health health = health(mock(FirebaseMessaging.class));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("pushDegraded", true);
        assertThat(String.valueOf(health.getDetails().get("reason"))).contains("backlog");
    }

    /** A machine with no Firebase credentials must not fail its own readiness probe. */
    @Test
    void missingCredentialsDegradeButDoNotFailTheProbe() {
        Health health = health(null);

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("pushDegraded", true);
        assertThat(health.getDetails()).containsEntry("provider", "NOT_CONFIGURED");
    }
}
