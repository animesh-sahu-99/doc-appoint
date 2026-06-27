package com.clinic.doc_appointment.util;

/**
 * Small helper for composing a person's display name from first/last parts.
 *
 * <p>Centralizes the {@code first + (last != null ? " " + last : "")} idiom that was
 * duplicated across the service response mappers, preserving the exact same output
 * (including the historic behavior where a non-null but empty last name yields a
 * trailing space).
 */
public final class NameUtils {

    private NameUtils() {
        // utility class — no instances
    }

    /**
     * Builds a display name. A {@code null} last name contributes nothing; otherwise
     * the last name is appended after a single space.
     */
    public static String fullName(String firstName, String lastName) {
        return firstName + (lastName != null ? " " + lastName : "");
    }
}
