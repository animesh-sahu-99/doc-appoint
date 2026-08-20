package com.clinic.doc_appointment.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Refuses to let the application start on a secret that is published in the repository.
 *
 * <h2>Why this has to throw</h2>
 *
 * <p>Both secrets are declared as {@code ${VAR:default}} with a working default. That form only
 * makes them <em>overridable</em> — an unset environment variable boots perfectly happily on the
 * value that is committed to git. For {@code jwt.secret} that means anyone with repository access
 * can mint a token for any user and any role; for {@code file.encryption.secret} it is the AES key
 * for every patient document, sitting next to the encrypted files themselves.
 *
 * <p>Logging an ERROR was the previous mitigation, and it is not one: nothing fails, nothing pages
 * anyone, and the line scrolls past in a startup log. A refused startup is impossible to miss and
 * happens before the service can accept a single request.
 *
 * <p>The {@code dev} profile is exempt, so a developer can still clone and run. That is the whole
 * point of the exemption — production is what must not be able to opt in by accident.
 */
public final class CommittedSecretGuard {

    private CommittedSecretGuard() {
    }

    /**
     * @param propertyName     the configuration key, named in the failure so the fix is obvious
     * @param envVarName       the environment variable that overrides it
     * @param value            the configured value
     * @param committedDefault the value published in the repository
     * @param minBytes         minimum UTF-8 length the algorithm requires
     * @param activeProfiles   raw {@code spring.profiles.active} value; {@code dev} exempts the check
     * @throws IllegalStateException if the value is too short, or is the committed default outside dev
     */
    public static void requireUsableSecret(String propertyName, String envVarName, String value,
                                           String committedDefault, int minBytes, String activeProfiles) {
        int length = value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;

        // Length is non-negotiable in every profile: a short key is not weak, it is broken.
        if (length < minBytes) {
            throw new IllegalStateException(propertyName + " must be at least " + minBytes
                    + " bytes, but was " + length + ". Set the " + envVarName + " environment variable.");
        }

        if (committedDefault.equals(value) && !isDevProfileActive(activeProfiles)) {
            throw new IllegalStateException(propertyName + " is still the development default that is "
                    + "committed to this repository, so it is public. Set the " + envVarName
                    + " environment variable, or run with the 'dev' profile if this really is a "
                    + "development machine.");
        }
    }

    /** True when {@code dev} appears in the comma-separated active profile list. */
    public static boolean isDevProfileActive(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        return Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch("dev"::equalsIgnoreCase);
    }
}
