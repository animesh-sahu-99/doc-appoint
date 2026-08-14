package com.clinic.doc_appointment.entity;

import com.clinic.doc_appointment.enums.NotificationType;
import com.clinic.doc_appointment.enums.PushOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

/**
 * One push notification awaiting delivery.
 *
 * <p>Written in the same transaction as its {@link Notification}, so a committed notification
 * always has a pending push and a rolled-back one has neither. {@link
 * com.clinic.doc_appointment.service.push.PushOutboxWorker} then delivers it off the request
 * thread, retrying with backoff — replacing the previous fire-and-forget send whose failures
 * vanished into a log line.
 *
 * <p><strong>Device tokens are deliberately not stored here.</strong> They are resolved from
 * {@code user_devices} at send time, so a device registered after enqueue still receives the
 * push and one deactivated in between is never attempted.
 *
 * <p><strong>No {@code @CreationTimestamp} / {@code @UpdateTimestamp}.</strong> Every write after
 * the initial insert is a bulk JPQL update, which bypasses Hibernate's lifecycle callbacks
 * entirely — an {@code @UpdateTimestamp} here would silently never fire. Both timestamps are set
 * by hand, in the entity factory and in each repository statement.
 *
 * <p><strong>No {@code @Version}.</strong> Concurrency is handled by compare-and-set bulk updates,
 * which optimistic locking would not see anyway.
 *
 * <p><strong>Why {@link Instant} and not {@code LocalDateTime}</strong> (which {@link Notification}
 * uses): the claim's entire correctness rests on {@code next_attempt_at <= now} being compared
 * consistently across instances, and this build pins {@code -Duser.timezone=Asia/Kolkata} in both
 * the Spring Boot and Surefire plugins. A zone or DST shift applied to a zone-less column would
 * make rows due early, or never due at all. Same reasoning as {@link RefreshToken} — please do not
 * "fix" this for consistency with the display timestamps elsewhere.
 */
@Entity
@Table(name = "push_outbox", indexes = {
        @Index(name = "idx_push_outbox_due", columnList = "status, next_attempt_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushOutboxMessage {

    /** Column width of {@link #lastError}. Declared first so the column annotation can use it. */
    public static final int MAX_ERROR_LENGTH = 500;

    /**
     * Internal identifier — never appears in an API or a URL, so it skips the {@code IdPrefix}
     * convention that user-facing ids follow. Same exception {@link RefreshToken} makes.
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private String id;

    /**
     * The notification this push belongs to. Unique, which makes enqueue idempotent and gives an
     * operator a direct join back to what the user actually sees.
     *
     * <p>Uniqueness comes from this annotation and is deliberately not repeated in
     * {@code @Table(indexes = ...)} — that would emit a second, redundant constraint.
     */
    @Column(name = "notification_id", nullable = false, unique = true)
    private String notificationId;

    /** Recipient. Tokens are looked up from this at send time. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    /** Deep-link target carried through to the client payload. */
    @Column(name = "related_entity_id")
    private String relatedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private PushOutboxStatus status = PushOutboxStatus.PENDING;

    /**
     * Incremented at <em>claim</em> time, before the send. A row that crashes its worker every
     * time therefore still walks toward DEAD instead of being redelivered forever.
     */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    /**
     * When this row next becomes claimable. Serves double duty: the retry deadline for a PENDING
     * row, and the lease deadline for an IN_FLIGHT one — which is why a worker that dies mid-send
     * needs no reaper to recover its rows.
     */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    /** Instance that holds the lease. Guards the outcome write against a stolen lease. */
    @Column(name = "claimed_by", length = 64)
    private String claimedBy;

    /**
     * Why the last attempt failed. Together with the DEAD retention window this is the only
     * forensic record that a push was abandoned.
     *
     * <p>Callers MUST truncate to {@link #MAX_ERROR_LENGTH}; an overflow would fail the outcome
     * write and strand the row IN_FLIGHT.
     */
    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Builds a row that is immediately due, mirroring the notification's payload.
     *
     * <p>Called inside the notification's own transaction — see
     * {@code NotificationService.sendNotification}.
     */
    public static PushOutboxMessage pendingFor(Notification notification, Instant now) {
        return PushOutboxMessage.builder()
                .notificationId(notification.getId())
                .userId(notification.getUserId())
                .title(notification.getTitle())
                .body(notification.getMessage())
                .type(notification.getType())
                .relatedEntityId(notification.getRelatedEntityId())
                .status(PushOutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** Truncates a failure description to what {@link #lastError} can hold. Null-safe. */
    public static String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
