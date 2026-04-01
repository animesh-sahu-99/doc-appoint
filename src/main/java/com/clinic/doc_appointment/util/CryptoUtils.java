package com.clinic.doc_appointment.util;

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
import java.security.SecureRandom;
import java.util.Arrays;

@Slf4j
@Component
public class CryptoUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16; // 16 bytes for CBC

    @Value("${file.encryption.secret}")
    private String encryptionKeyStr;

    private SecretKeySpec getSecretKey() {
        byte[] keyBytes = Arrays.copyOf(encryptionKeyStr.getBytes(), 32); // Ensure it's 256-bit (32 bytes)
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Writes 12-byte IV to the output stream, then wraps the output stream with a CipherOutputStream.
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
     * Reads the 12-byte IV from the input stream, then wraps the input stream with a CipherInputStream.
     */
    public InputStream getDecryptedInputStream(InputStream in) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        int bytesRead = in.read(iv);
        if (bytesRead != IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted file: IV is missing or corrupted");
        }

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), ivSpec);

        return new CipherInputStream(in, cipher);
    }
}
