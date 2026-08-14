package com.clinic.doc_appointment.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenMaskerTest {

    /** Shape of a real FCM registration token: long, with a long shared prefix. */
    private static final String REAL_TOKEN =
            "fMEr7pQnSTa8zK3vXbNhJd:APA91bH9xYzQwErTyUiOpAsDfGhJkLzXcVbNmQwErTyUiOpAsDfGhJkL";

    @Test
    void neverRevealsTheWholeToken() {
        String masked = TokenMasker.mask(REAL_TOKEN);

        assertFalse(masked.contains(REAL_TOKEN), "the full token must never reach a log");
        assertTrue(masked.length() < REAL_TOKEN.length());
    }

    @Test
    void keepsEnoughToCorrelateOneDeviceAcrossLogLines() {
        String masked = TokenMasker.mask(REAL_TOKEN);

        assertTrue(masked.startsWith("fMEr7p"), () -> "expected a head fragment, got: " + masked);
        assertTrue(masked.contains("hJkL"), () -> "expected a tail fragment, got: " + masked);
        assertTrue(masked.contains("len=" + REAL_TOKEN.length()));
        assertTrue(masked.chars().allMatch(c -> c < 128), () -> "log output must stay ASCII: " + masked);
    }

    /**
     * The regression this class exists for: the old inline {@code token.substring(0, 20)} sat
     * inside a catch block, so a short token threw from within the error handler and aborted
     * delivery for every remaining device.
     */
    @Test
    void doesNotThrowOnTokensShorterThanTheMaskWindow() {
        assertDoesNotThrow(() -> TokenMasker.mask("abc"));
        assertEquals("<short:3>", TokenMasker.mask("abc"));
        assertEquals("<short:1>", TokenMasker.mask("x"));
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("<null>", TokenMasker.mask(null));
        assertEquals("<blank>", TokenMasker.mask(""));
        assertEquals("<blank>", TokenMasker.mask("   "));
    }

    @Test
    void isTotalAcrossEveryPrefixOfARealToken() {
        // No length may throw — the whole point of the helper.
        for (int i = 0; i <= REAL_TOKEN.length(); i++) {
            String prefix = REAL_TOKEN.substring(0, i);
            assertDoesNotThrow(() -> TokenMasker.mask(prefix), "threw at length " + i);
        }
    }
}
