package com.clinic.doc_appointment.enums;

/**
 * The two user roles in the system. Replaces the scattered {@code "DOCTOR"}/{@code "PATIENT"}
 * and {@code "ROLE_DOCTOR"}/{@code "ROLE_PATIENT"} magic strings.
 *
 * <p><strong>Important:</strong> {@link #authority()} must keep returning exactly
 * {@code "ROLE_DOCTOR"}/{@code "ROLE_PATIENT"} — those strings gate Spring Security
 * {@code @PreAuthorize("hasRole('...')")} checks, are embedded in already-issued JWTs, and
 * form part of the {@code AuthResponse} wire contract.
 */
public enum Role {
    DOCTOR,
    PATIENT;

    /** Spring Security authority form, e.g. {@code "ROLE_DOCTOR"}. */
    public String authority() {
        return "ROLE_" + name();
    }

    /** Parses either {@code "ROLE_DOCTOR"} or {@code "DOCTOR"} (case-insensitive) into a {@link Role}. */
    public static Role fromAuthority(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Role value must not be null");
        }
        String normalized = value.toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return Role.valueOf(normalized);
    }
}
