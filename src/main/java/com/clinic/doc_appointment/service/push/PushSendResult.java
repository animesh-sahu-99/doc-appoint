package com.clinic.doc_appointment.service.push;

import java.time.Duration;

/**
 * Outcome for exactly one device token.
 *
 * <p>{@code errorCode} is a {@link String}, not a provider enum, on purpose: it keeps Firebase
 * vocabulary from leaking through the {@link PushNotificationProvider} port, lets a future APNs
 * adapter use its own codes, and stores directly into {@code push_outbox.last_error} with no
 * converter.
 *
 * <p>Nullability, documented rather than annotated (record components cannot carry
 * {@code @Nullable}):
 * <ul>
 *   <li>{@code providerMessageId} — non-null only when {@code outcome == DELIVERED}</li>
 *   <li>{@code errorCode} / {@code errorDetail} — non-null for every non-delivered outcome</li>
 *   <li>{@code retryAfter} — usually null; set only when the provider supplied a hint</li>
 * </ul>
 *
 * <p>{@code errorDetail} MUST already be safe to log and store: no raw token, no credentials.
 */
public record PushSendResult(
        String token,
        PushOutcome outcome,
        String providerMessageId,
        String errorCode,
        String errorDetail,
        Duration retryAfter
) {

    public static PushSendResult delivered(String token, String providerMessageId) {
        return new PushSendResult(token, PushOutcome.DELIVERED, providerMessageId, null, null, null);
    }

    public static PushSendResult failure(String token, PushOutcome outcome,
                                         String errorCode, String errorDetail) {
        return new PushSendResult(token, outcome, null, errorCode, errorDetail, null);
    }

    public static PushSendResult failure(String token, PushOutcome outcome, String errorCode,
                                         String errorDetail, Duration retryAfter) {
        return new PushSendResult(token, outcome, null, errorCode, errorDetail, retryAfter);
    }

    public boolean isDelivered() {
        return outcome == PushOutcome.DELIVERED;
    }

    /** The worker should schedule another attempt for this recipient. */
    public boolean isRetryable() {
        return outcome == PushOutcome.RETRY || outcome == PushOutcome.PROVIDER_UNCONFIGURED;
    }

    /** Copies this result onto a different outcome, preserving the error context. */
    public PushSendResult withOutcome(PushOutcome replacement) {
        return new PushSendResult(token, replacement, providerMessageId, errorCode, errorDetail, retryAfter);
    }
}
