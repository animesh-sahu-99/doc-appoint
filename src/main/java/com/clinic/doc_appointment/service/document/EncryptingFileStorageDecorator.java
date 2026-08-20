package com.clinic.doc_appointment.service.document;

import com.clinic.doc_appointment.exception.FileStorageException;
import com.clinic.doc_appointment.util.CryptoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Decorator that adds AES-256 encryption-at-rest on top of {@link LocalFileStorageService}.
 *
 * <p>On store, the content is encrypted into a buffer ({@link CryptoUtils} prepends a random IV)
 * and the resulting ciphertext is persisted raw under a {@code <uuid><ext>.enc} name; on load, the
 * raw ciphertext stream is wrapped with a decrypting stream. The on-disk format is byte-identical
 * to the previous combined implementation, so existing encrypted files keep round-tripping.
 *
 * <p>This is the sole {@link FileStorageService} bean, so callers ({@code DocumentService}) are
 * unchanged and transparently get encryption.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptingFileStorageDecorator implements FileStorageService {

    private final LocalFileStorageService storage;
    private final CryptoUtils cryptoUtils;

    @Override
    public String storeFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        // Unique, encryption-suffixed name (unchanged convention).
        String fileName = UUID.randomUUID() + extension + ".enc";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = file.getInputStream();
             OutputStream encrypted = cryptoUtils.getEncryptedOutputStream(buffer)) {
            in.transferTo(encrypted);
            // closing 'encrypted' (reverse order) flushes the cipher's final block into the buffer
        } catch (Exception ex) {
            // getEncryptedOutputStream declares `throws Exception` (cipher setup), so this cannot be narrowed.
            throw new FileStorageException("Could not store and encrypt file " + fileName + ". Please try again!", ex);
        }

        storage.store(new ByteArrayInputStream(buffer.toByteArray()), fileName);
        log.info("Successfully encrypted and stored file: {}", fileName);
        return fileName;
    }

    /**
     * Opens a decrypting stream over the stored ciphertext.
     *
     * <p>The underlying stream is closed by hand if wrapping it fails. Nobody else can do it: the
     * caller only ever receives the wrapped {@link Resource}, so when {@code getDecryptedInputStream}
     * threw — a truncated file, a rotated key, any cipher init failure — the open file descriptor
     * was simply abandoned. A corrupt file plus a retrying client leaked one descriptor per attempt
     * until the process hit its limit.
     */
    @Override
    public Resource loadFileAsResource(String fileName) {
        Resource encrypted = storage.load(fileName);
        InputStream raw = null;
        try {
            raw = encrypted.getInputStream();
            return new InputStreamResource(cryptoUtils.getDecryptedInputStream(raw));
        } catch (Exception ex) {
            closeQuietly(raw, fileName);
            throw new FileStorageException("Could not read file " + fileName, ex);
        }
    }

    @Override
    public void deleteFile(String fileName) {
        storage.delete(fileName);
        log.info("Deleted encrypted file: {}", fileName);
    }

    private static void closeQuietly(InputStream stream, String fileName) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException suppressed) {
            log.warn("Failed to close the underlying stream for {} after a decryption failure", fileName, suppressed);
        }
    }
}
