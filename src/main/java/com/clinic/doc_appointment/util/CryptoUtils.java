package com.clinic.doc_appointment.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES encryption-at-rest for stored documents.
 *
 * <p><strong>On the key:</strong> the first 32 UTF-8 bytes of the configured secret are used
 * directly. Three things about that were wrong before:
 * <ul>
 *   <li>{@code getBytes()} with no charset made the key depend on the JVM's default encoding, so the
 *       same secret on two machines with different {@code file.encoding} produced two different keys
 *       and documents written by one became undecryptable by the other;</li>
 *   <li>{@code Arrays.copyOf} silently <em>zero-padded</em> anything shorter than 32 bytes, so
 *       {@code FILE_ENC_SECRET=short} yielded a key with 27 known-zero bytes and no complaint;</li>
 *   <li>nothing rejected the committed default, unlike {@code jwt.secret} which at least logged.</li>
 * </ul>
 *
 * <p>Derivation is unchanged for any secret already 32 bytes or longer — including the committed
 * default, which is ASCII and exactly 32 characters — so files encrypted before this change still
 * decrypt. Only the previously zero-padded short-key case changes behaviour, and that case now
 * fails loudly instead of pretending to be AES-256.
 *
 * <p><strong>Known limitation, deliberately not changed here:</strong> {@code AES/CBC/PKCS5Padding}
 * is unauthenticated. Stored ciphertext is malleable and the decrypt path is a padding oracle;
 * {@code AES/GCM/NoPadding} is the right target. That is a change of on-disk format requiring every
 * existing file to be re-encrypted, so it needs a migration rather than an edit.
 */
@Slf4j
@Component
public class CryptoUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;  // 16 bytes for CBC
    private static final int KEY_LENGTH = 32; // 32 bytes for AES-256

    /** The committed development secret. Fine locally, never acceptable in production. */
    private static final String DEFAULT_DEV_SECRET = "my-secret-encryption-key-32bytes!";

    private final String encryptionKeyStr;
    private final String activeProfiles;

    /**
     * Constructor injection rather than {@code @Value} fields, so the class is testable without
     * reflection — the same reasoning {@code JwtService} documents.
     */
    public CryptoUtils(@Value("${file.encryption.secret}") String encryptionKeyStr,
                       @Value("${spring.profiles.active:}") String activeProfiles) {
        this.encryptionKeyStr = encryptionKeyStr;
        this.activeProfiles = activeProfiles;
    }

    @PostConstruct
    void validateSecret() {
        CommittedSecretGuard.requireUsableSecret(
                "file.encryption.secret", "FILE_ENC_SECRET", encryptionKeyStr,
                DEFAULT_DEV_SECRET, KEY_LENGTH, activeProfiles);

        if (DEFAULT_DEV_SECRET.equals(encryptionKeyStr)) {
            log.warn("file.encryption.secret is the committed development default; permitted only "
                    + "because the 'dev' profile is active. Patient documents encrypted with it are "
                    + "readable by anyone with this repository.");
        }
    }

    private SecretKeySpec getSecretKey() {
        byte[] keyBytes = Arrays.copyOf(encryptionKeyStr.getBytes(StandardCharsets.UTF_8), KEY_LENGTH);
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Writes the 16-byte IV to the output stream, then wraps it with a CipherOutputStream.
     */
    public OutputStream getEncryptedOutputStream(OutputStream out) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        // Prepend IV to the file so it can be extracted during decryption
        out.write(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), ivSpec);

        return new CipherOutputStream(out, cipher);
    }

    /**
     * Reads the 16-byte IV from the input stream, then wraps it with a CipherInputStream.
     *
     * <p>Uses {@code readNBytes} rather than a single {@code read}: {@code read(byte[])} is only
     * required to return <em>at least one</em> byte, so a short read on a perfectly valid file would
     * have been reported as a corrupted one.
     *
     * <p>If cipher setup fails after the IV has been consumed, the caller's stream is left open on
     * purpose — {@code EncryptingFileStorageDecorator} owns it and closes it, since it is the one
     * that opened it.
     */
    public InputStream getDecryptedInputStream(InputStream in) throws Exception {
        byte[] iv = in.readNBytes(IV_LENGTH);
        if (iv.length != IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted file: IV is missing or corrupted");
        }

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), ivSpec);

        return new CipherInputStream(in, cipher);
    }
}
