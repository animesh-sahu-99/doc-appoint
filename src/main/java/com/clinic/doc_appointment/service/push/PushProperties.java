package com.clinic.doc_appointment.service.push;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Push outbox delivery settings.
 *
 * <p>Uses {@code @ConfigurationProperties} rather than the constructor-{@code @Value} style used
 * elsewhere ({@code JwtService}, {@code RefreshTokenService}). Deliberate departure, not drift:
 * eleven {@code @Value} parameters on one constructor trades one kind of readability for a worse
 * one.
 */
@Component
@ConfigurationProperties(prefix = "push.outbox")
@Getter
@Setter
public class PushProperties {

    /** False pauses the worker. Rows keep queuing and drain when it is re-enabled — not discarded. */
    private boolean enabled = true;

    /** Worst-case added push latency, since the worker does all sending. */
    private long pollMillis = 1000;

    /** Rows per drain cycle. Also bounds how long one cycle can block on the network. */
    private int batchSize = 50;

    /**
     * How long a claimed row stays leased before becoming claimable again — the crash-recovery
     * window.
     *
     * <p>Must comfortably exceed the worst-case send time. The Firebase SDK's default timeouts are
     * 60s, so a 60s lease would expire exactly as a hung call returns and guarantee a duplicate
     * push; 120s stays clear of that.
     */
    private long leaseMillis = 120_000;

    /** Attempts before a row is dead-lettered. */
    private int maxAttempts = 5;

    /**
     * First retry delay; doubles each attempt with ±20% jitter, capped by {@link #maxBackoffMillis}.
     *
     * <p>Short on purpose. The WebSocket broadcast and the in-app notification list have already
     * delivered the content — push is the convenience channel — and an "Appointment Confirmed"
     * banner arriving forty minutes late is worse than none at all.
     */
    private long backoffMillis = 15_000;

    private double backoffMultiplier = 2.0;

    private long maxBackoffMillis = 900_000;

    private long cleanupMillis = 3_600_000;

    private int retainSentDays = 3;

    /** Dead rows are the forensic record of an abandoned push — keep them far longer. */
    private int retainDeadDays = 30;

    private long reportMillis = 300_000;

    /** Pending rows above which the periodic health line escalates to WARN. */
    private long backlogWarn = 100;
}
