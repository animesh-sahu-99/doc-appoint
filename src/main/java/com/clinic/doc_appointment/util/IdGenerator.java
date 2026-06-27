package com.clinic.doc_appointment.util;

import com.clinic.doc_appointment.enums.IdPrefix;

import java.util.UUID;

/**
 * Factory Method for human-readable, prefixed entity identifiers (e.g. {@code "DOC-<uuid>"}).
 *
 * <p>Centralizes the {@code prefix + UUID.randomUUID()} concatenation that each entity
 * previously hard-coded in its {@code @PrePersist} callback. Output is byte-identical to
 * the previous inline form.
 */
public final class IdGenerator {

    private IdGenerator() {
        // utility class — no instances
    }

    public static String withPrefix(IdPrefix prefix) {
        return prefix.getPrefix() + UUID.randomUUID();
    }
}
