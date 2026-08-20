package com.clinic.doc_appointment.service.document;

import com.clinic.doc_appointment.util.CryptoUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptingFileStorageDecoratorTest {

    @TempDir
    Path tempDir;

    private EncryptingFileStorageDecorator newDecorator() {
        // Constructor injection replaced the @Value field, so no reflection is needed to set the key.
        // The key is 33 bytes, comfortably over the 32 the AES-256 guard requires.
        CryptoUtils crypto = new CryptoUtils("unit-test-secret-key-please-change", "");
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString());
        storage.init();
        return new EncryptingFileStorageDecorator(storage, crypto);
    }

    @Test
    void storesCiphertextAndRoundTripsToPlaintext() throws Exception {
        EncryptingFileStorageDecorator decorator = newDecorator();
        byte[] original = "Sensitive medical report contents".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", original);

        String storedName = decorator.storeFile(file);

        // Name convention preserved: <uuid>.pdf.enc
        assertTrue(storedName.endsWith(".pdf.enc"), "stored name should keep the .enc convention");

        // On disk it must NOT be plaintext (encrypted at rest)
        byte[] onDisk = Files.readAllBytes(tempDir.resolve(storedName));
        assertFalse(Arrays.equals(original, onDisk), "file on disk must be encrypted");

        // Round-trips back to the original bytes
        Resource loaded = decorator.loadFileAsResource(storedName);
        try (InputStream in = loaded.getInputStream()) {
            assertArrayEquals(original, in.readAllBytes());
        }
    }

    @Test
    void deleteRemovesTheFile() {
        EncryptingFileStorageDecorator decorator = newDecorator();
        MockMultipartFile file = new MockMultipartFile("file", "scan.jpg", "image/jpeg", "x".getBytes());
        String storedName = decorator.storeFile(file);
        assertTrue(Files.exists(tempDir.resolve(storedName)));

        decorator.deleteFile(storedName);

        assertFalse(Files.exists(tempDir.resolve(storedName)));
    }
}
