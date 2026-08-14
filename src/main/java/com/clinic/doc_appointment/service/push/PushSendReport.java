package com.clinic.doc_appointment.service.push;

import java.util.List;

/**
 * Per-token outcomes for one call to {@link PushNotificationProvider#send}.
 *
 * <p>Only {@code results} is stored; every count is derived, so a count can never disagree with
 * the list it summarises. The accessors are limited to what {@link PushOutboxWorker} actually
 * calls — deliberately not a general-purpose query API.
 */
public record PushSendReport(List<PushSendResult> results) {

    public PushSendReport {
        results = (results == null) ? List.of() : List.copyOf(results);
    }

    public static PushSendReport empty() {
        return new PushSendReport(List.of());
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    public long deliveredCount() {
        return results.stream().filter(PushSendResult::isDelivered).count();
    }

    /** True when at least one recipient is worth another attempt. */
    public boolean anyRetryable() {
        return results.stream().anyMatch(PushSendResult::isRetryable);
    }

    /** Tokens the worker must deactivate in {@code user_devices}. */
    public List<String> deadTokens() {
        return results.stream()
                .filter(r -> r.outcome() == PushOutcome.DEAD_TOKEN)
                .map(PushSendResult::token)
                .toList();
    }

    /**
     * True only when the adapter had no usable provider at all — no credentials configured on
     * this instance, so nothing was even attempted.
     *
     * <p>The worker treats this as {@code SKIPPED} rather than a failure, which is what keeps a
     * developer machine without Firebase credentials from filling the table with DEAD rows.
     */
    public boolean providerUnavailable() {
        return !results.isEmpty()
                && results.stream().allMatch(r -> r.outcome() == PushOutcome.PROVIDER_UNCONFIGURED
                        && FcmErrorClassifier.NO_PROVIDER.equals(r.errorCode()));
    }
}
