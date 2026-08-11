package com.clinic.doc_appointment.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenHasherTest {

    /** Known SHA-256 of the empty string — pins the algorithm and the hex encoding. */
    private static final String SHA256_OF_EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void matchesKnownAnswerForEmptyString() {
        assertEquals(SHA256_OF_EMPTY, TokenHasher.sha256Hex(""));
    }

    @Test
    void isDeterministic() {
        String token = "kQ7xR2mVpL9-fT4wA1zN8sJ6bC0dE3gH5iK7lM9oP2q";
        assertEquals(TokenHasher.sha256Hex(token), TokenHasher.sha256Hex(token));
    }

    @Test
    void producesLowercaseHexOfFixedWidth() {
        String hash = TokenHasher.sha256Hex("some-refresh-token");
        assertEquals(64, hash.length(), "token_hash column is sized length = 64");
        assertTrue(hash.matches("[0-9a-f]{64}"), "expected lowercase hex, got: " + hash);
    }

    @Test
    void distinctInputsProduceDistinctHashes() {
        assertTrue(TokenHasher.sha256Hex("token-a").equals(TokenHasher.sha256Hex("token-a")));
        assertTrue(!TokenHasher.sha256Hex("token-a").equals(TokenHasher.sha256Hex("token-b")));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> TokenHasher.sha256Hex(null));
    }

    /**
     * Regression guard for the reason a fresh MessageDigest is created per call: a shared,
     * cached instance would interleave under concurrent refreshes and yield wrong digests.
     */
    @Test
    void isThreadSafeUnderConcurrentHashing() throws Exception {
        String token = "concurrent-refresh-token";
        String expected = TokenHasher.sha256Hex(token);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> jobs = IntStream.range(0, 500)
                    .<Callable<String>>mapToObj(i -> () -> TokenHasher.sha256Hex(token))
                    .toList();

            Set<String> results = new HashSet<>();
            for (Future<String> future : pool.invokeAll(jobs)) {
                results.add(future.get());
            }
            assertEquals(Set.of(expected), results, "concurrent hashing produced inconsistent digests");
        } finally {
            pool.shutdownNow();
        }
    }
}
