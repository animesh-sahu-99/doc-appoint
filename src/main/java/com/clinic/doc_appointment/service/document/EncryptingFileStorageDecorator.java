package com.clinic.doc_appointment.service.document;

import com.clinic.doc_appointment.util.CryptoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
        String fileName = UUID.randomUUID().toString() + extension + ".enc";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = file.getInputStream();
             OutputStream encrypted = cryptoUtils.getEncryptedOutputStream(buffer)) {
            in.transferTo(encrypted);
            // closing 'encrypted' (reverse order) flushes the cipher's final block into the buffer
        } catch (Exception ex) {
            throw new RuntimeException("Could not store and encrypt file " + fileName + ". Please try again!", ex);
        }

        storage.store(new ByteArrayInputStream(buffer.toByteArray()), fileName);
        log.info("Successfully encrypted and stored file: {}", fileName);
        return fileName;
    }

    @Override
    public Resource loadFileAsResource(String fileName) {
        try {
            Resource encrypted = storage.load(fileName);
            InputStream decrypted = cryptoUtils.getDecryptedInputStream(encrypted.getInputStream());
            return new InputStreamResource(decrypted);
        } catch (Exception ex) {
            throw new RuntimeException("Could not read file " + fileName, ex);
        }
    }

    @Override
    public void deleteFile(String fileName) {
        storage.delete(fileName);
        log.info("Deleted encrypted file: {}", fileName);
    }
}
