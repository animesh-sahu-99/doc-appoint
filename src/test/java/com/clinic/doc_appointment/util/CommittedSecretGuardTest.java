package com.clinic.doc_appointment.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Startup must refuse a secret that is published in the repository.
 *
 * <p>Both secrets are declared as {@code ${VAR:default}}, which only makes them overridable — an
 * unset environment variable boots on the committed value. Logging an ERROR, the previous
 * mitigation, changes nothing that anyone notices.
 */
class CommittedSecretGuardTest {

    private static final String COMMITTED = "committed-development-secret-value-32b";
    private static final String OWN = "an-operator-supplied-secret-of-good-length";
    private static final int MIN_BYTES = 32;

    private void check(String value, String activeProfiles) {
        CommittedSecretGuard.requireUsableSecret(
                "some.secret", "SOME_SECRET", value, COMMITTED, MIN_BYTES, activeProfiles);
    }

    @Test
    void acceptsAnOperatorSuppliedSecret() {
        assertThatCode(() -> check(OWN, "")).doesNotThrowAnyException();
    }

    @Test
    void refusesTheCommittedDefaultWhenNoProfileIsActive() {
        assertThatThrownBy(() -> check(COMMITTED, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("committed to this repository")
                .hasMessageContaining("SOME_SECRET");
    }

    @Test
    void refusesTheCommittedDefaultInProduction() {
        assertThatThrownBy(() -> check(COMMITTED, "prod"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** The dev exemption is the whole reason this can be enforced at all. */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "DEV", " dev ", "dev,local", "local,dev"})
    void allowsTheCommittedDefaultUnderTheDevProfile(String activeProfiles) {
        assertThatCode(() -> check(COMMITTED, activeProfiles)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"development", "devops", "prod-dev-ish"})
    void doesNotMistakeASimilarlyNamedProfileForDev(String activeProfiles) {
        assertThatThrownBy(() -> check(COMMITTED, activeProfiles))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Length is non-negotiable in every profile, including dev. A short key is not weak, it is
     * broken — and the old code silently zero-padded it to look like AES-256.
     */
    @Test
    void refusesAShortSecretEvenUnderTheDevProfile() {
        assertThatThrownBy(() -> check("too-short", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void refusesAnAbsentSecret() {
        assertThatThrownBy(() -> check(null, "dev"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Length is measured in UTF-8 bytes, not characters. */
    @Test
    void measuresLengthInBytesNotCharacters() {
        String sixteenMultibyteChars = "é".repeat(16);   // 16 chars, 32 bytes
        assertThatCode(() -> check(sixteenMultibyteChars, "")).doesNotThrowAnyException();

        String fifteenMultibyteChars = "é".repeat(15);   // 30 bytes
        assertThatThrownBy(() -> check(fifteenMultibyteChars, ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
