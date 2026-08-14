package com.clinic.doc_appointment.util;

/**
 * Renders a device token safely for logs.
 *
 * <p>Replaces the previous inline {@code token.substring(0, 20)} calls, which sat <em>inside</em>
 * exception handlers: a token shorter than 20 characters threw
 * {@link StringIndexOutOfBoundsException} from within the handler, escaped the send method, and
 * aborted delivery for every remaining device. This is total by construction — null-safe,
 * length-safe, and never throws.
 *
 * <p>No log statement anywhere may take a raw FCM token as an argument.
 */
public final class TokenMasker {

    private static final int HEAD = 6;
    private static final int TAIL = 4;

    /** Below this, a value is not a usable token, so only its length is worth emitting. */
    private static final int MIN_MASKABLE = HEAD + TAIL + 2;

    private TokenMasker() {
        // utility class — no instances
    }

    /**
     * Head-and-tail rather than head-only: real FCM tokens run ~140-180 characters and share a
     * long common prefix per app, so a leading fragment barely distinguishes devices. Six plus
     * four characters and the length is enough to correlate one device across log lines, and
     * nowhere near enough to reconstruct the token.
     */
    public static String mask(String token) {
        if (token == null) {
            return "<null>";
        }
        if (token.isBlank()) {
            return "<blank>";
        }
        int length = token.length();
        if (length <= MIN_MASKABLE) {
            return "<short:" + length + ">";
        }
        // ASCII separator on purpose: log files and consoles vary in encoding, and a mangled
        // ellipsis in the middle of a token fragment is worse than useless.
        return token.substring(0, HEAD) + ".." + token.substring(length - TAIL) + " (len=" + length + ")";
    }
}
