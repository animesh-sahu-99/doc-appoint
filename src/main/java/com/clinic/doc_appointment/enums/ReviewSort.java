package com.clinic.doc_appointment.enums;

import org.springframework.data.domain.Sort;

/**
 * Sort orders offered by the doctor-reviews listing.
 *
 * <p>Replaces a chain of {@code "high"}/{@code "low"} string comparisons in which the documented
 * default, {@code "recent"}, appeared nowhere — so it fell through to the same branch as a typo or
 * any unrecognised value. Encoding the options here makes the set explicit, keeps each order next
 * to its name, and lets a new order be added without touching the service (OCP).
 */
public enum ReviewSort {

    /** Newest first — the default. */
    RECENT(Sort.by(Sort.Direction.DESC, "createdAt")),

    /** Highest rating first, newest first within a rating. */
    HIGH(Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"))),

    /** Lowest rating first, newest first within a rating. */
    LOW(Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt")));

    private final Sort sort;

    ReviewSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }

    /**
     * Parses a client-supplied value, case-insensitively, falling back to {@link #RECENT}.
     *
     * <p>Lenient on purpose: this is a listing order, not a command. Rejecting an unknown value
     * would turn a cosmetic mismatch into a failed screen, and the existing client already sends
     * {@code "recent"} explicitly.
     */
    public static ReviewSort fromRequest(String value) {
        if (value == null || value.isBlank()) {
            return RECENT;
        }
        for (ReviewSort candidate : values()) {
            if (candidate.name().equalsIgnoreCase(value.trim())) {
                return candidate;
            }
        }
        return RECENT;
    }
}
