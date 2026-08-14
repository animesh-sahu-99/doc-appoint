package com.clinic.doc_appointment.service.push;

/**
 * What happened when we tried to deliver to one device, expressed in provider-neutral terms.
 *
 * <p>Every constant here drives a genuinely different action in
 * {@link PushOutboxWorker} — that is the test for whether one deserves to exist. A separate
 * "throttled" outcome was folded into {@link #RETRY} for exactly this reason: the worker's
 * response was identical, and the only difference (wait longer) is carried by
 * {@link PushSendResult#retryAfter()}.
 */
public enum PushOutcome {

    /** The provider accepted the message for this token. */
    DELIVERED,

    /** Transient provider-side failure. Try again with backoff. */
    RETRY,

    /** This token will never work again. Deactivate the device row and stop targeting it. */
    DEAD_TOKEN,

    /**
     * The provider rejected our <em>payload</em>. Terminal for the outbox row, and crucially
     * <strong>no token is deactivated</strong> — the devices are fine, our message is not.
     */
    INVALID_MESSAGE,

    /**
     * Provider credentials or configuration are broken. Retrying is harmless but will never
     * succeed until a human intervenes, so the worker retries on a long delay
     * <em>without</em> burning the attempt budget — otherwise a weekend of expired credentials
     * would dead-letter the entire queue.
     */
    PROVIDER_UNCONFIGURED,

    /** Never handed to the provider — e.g. a blank token filtered out before the batch. */
    SKIPPED
}
