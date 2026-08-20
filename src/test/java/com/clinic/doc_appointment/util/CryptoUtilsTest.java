package com.clinic.doc_appointment.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoUtilsTest {

    /** The value committed in application.yaml, which is therefore public. */
    private static final String COMMITTED_DEFAULT = "my-secret-encryption-key-32bytes!";
    private static final String OWN_KEY = "an-operator-supplied-file-key-of-length";

    @Test
    void refusesTheCommittedDefaultOutsideDev() {
        CryptoUtils crypto = new CryptoUtils(COMMITTED_DEFAULT, "");

        assertThatThrownBy(crypto::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FILE_ENC_SECRET");
    }

    @Test
    void allowsTheCommittedDefaultUnderDev() {
        assertThatCode(new CryptoUtils(COMMITTED_DEFAULT, "dev")::validateSecret)
                .doesNotThrowAnyException();
    }

    /**
     * The old derivation zero-padded anything short of 32 bytes, so {@code FILE_ENC_SECRET=short}
     * silently produced a key with 27 known-zero bytes while still calling itself AES-256.
     */
    @Test
    void refusesAShortKeyRatherThanZeroPaddingIt() {
        assertThatThrownBy(new CryptoUtils("short", "dev")::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void roundTripsContentThroughTheCipher() throws Exception {
        CryptoUtils crypto = new CryptoUtils(OWN_KEY, "");
        crypto.validateSecret();

        byte[] plaintext = "Discharge summary: patient stable.".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream stored = new ByteArrayOutputStream();
        try (OutputStream cipherOut = crypto.getEncryptedOutputStream(stored)) {
            cipherOut.write(plaintext);
        }

        assertThat(stored.toByteArray()).isNotEqualTo(plaintext);
        // 16-byte IV is prepended, so the stored form is always longer than the input.
        assertThat(stored.size()).isGreaterThan(plaintext.length);

        try (InputStream cipherIn = crypto.getDecryptedInputStream(
                new ByteArrayInputStream(stored.toByteArray()))) {
            assertThat(cipherIn.readAllBytes()).isEqualTo(plaintext);
        }
    }

    /**
     * Derivation must stay byte-identical for keys of 32 bytes or more, or every document already on
     * disk becomes undecryptable. Two instances built from the same secret produce interchangeable
     * ciphertext; a different secret does not.
     */
    @Test
    void theSameSecretAlwaysDerivesTheSameKey() throws Exception {
        byte[] plaintext = "Prescription: 5mg daily".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream stored = new ByteArrayOutputStream();
        CryptoUtils writer = new CryptoUtils(OWN_KEY, "");
        try (OutputStream out = writer.getEncryptedOutputStream(stored)) {
            out.write(plaintext);
        }

        CryptoUtils separateInstance = new CryptoUtils(OWN_KEY, "");
        try (InputStream in = separateInstance.getDecryptedInputStream(
                new ByteArrayInputStream(stored.toByteArray()))) {
            assertThat(in.readAllBytes()).isEqualTo(plaintext);
        }

        CryptoUtils wrongKey = new CryptoUtils("a-completely-different-file-key-value!!", "");
        assertThatThrownBy(() -> {
            try (InputStream in = wrongKey.getDecryptedInputStream(
                    new ByteArrayInputStream(stored.toByteArray()))) {
                in.readAllBytes();
            }
        }).isInstanceOf(Exception.class);
    }

    /**
     * A truncated file must be rejected as corrupt, and {@code readNBytes} is what makes that
     * reliable — {@code read(byte[])} may legally return fewer bytes than asked for on a valid file.
     */
    @Test
    void rejectsAFileTooShortToHoldAnIv() {
        CryptoUtils crypto = new CryptoUtils(OWN_KEY, "");

        assertThatThrownBy(() -> crypto.getDecryptedInputStream(new ByteArrayInputStream(new byte[8])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IV is missing or corrupted");
    }
}
