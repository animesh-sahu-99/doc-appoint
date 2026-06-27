package com.clinic.doc_appointment.enums;

/**
 * Single source of truth for entity id prefixes used by
 * {@link com.clinic.doc_appointment.util.IdGenerator}.
 *
 * <p><strong>Note:</strong> {@link #DOCTOR} and {@link #DOCUMENT} intentionally share the
 * {@code "DOC-"} prefix to match the existing persisted identifiers. Do NOT change these
 * values — already-stored ids would stop round-tripping.
 */
public enum IdPrefix {
    DOCTOR("DOC-"),
    PATIENT("PAT-"),
    APPOINTMENT("APPOINTMENT-"),
    SLOT("SLOT-"),
    REVIEW("REVIEW-"),
    PAYMENT("PAYMENT-"),
    NOTIFICATION("NOT-"),
    DOCUMENT("DOC-");

    private final String prefix;

    IdPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
