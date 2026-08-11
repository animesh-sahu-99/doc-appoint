package com.clinic.doc_appointment.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One-way hashing for high-entropy bearer secrets (refresh tokens), so the database never
 * holds a usable credential.
 *
 * <p><strong>Why SHA-256 and not BCrypt.</strong> The input is 256 bits of {@code SecureRandom}
 * output, not a human-chosen password — it is not guessable, so a deliberately slow KDF buys
 * nothing. It would also cost correctness: BCrypt salts per row, which turns lookup into an
 * O(n) table scan, whereas a plain digest gives a unique indexed lookup on {@code token_hash}.
 * (BCrypt additionally truncates silently at 72 bytes.) Hashing high-entropy tokens with a
 * fast digest is the standard practice.
 */
public final class TokenHasher {

    private TokenHasher() {
        // utility class — no instances
    }

    /**
     * SHA-256 of {@code raw}, lowercase hex (64 chars).
     *
     * <p>A fresh {@link MessageDigest} is created per call on purpose: the class is stateful and
     * not thread-safe, so a cached static instance would interleave and corrupt digests under
     * the concurrent refreshes this system is explicitly designed to handle.
     */
    public static String sha256Hex(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Token to hash must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS for every conforming JRE; unreachable in practice.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
