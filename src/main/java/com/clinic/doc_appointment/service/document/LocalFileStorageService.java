package com.clinic.doc_appointment.service.document;

import com.clinic.doc_appointment.exception.FileStorageException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Plain local-disk byte storage. Knows nothing about encryption — it persists and serves raw
 * bytes under a caller-supplied file name. Encryption is layered on top by
 * {@link EncryptingFileStorageDecorator} (Decorator pattern); the backend could later be swapped
 * (e.g. S3) without touching the encryption logic.
 */
@Slf4j
@Component
public class LocalFileStorageService {

    private final Path fileStorageLocation;

    public LocalFileStorageService(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    /** Persists the given bytes under {@code fileName} and returns that name. */
    public String store(InputStream content, String fileName) {
        Path targetLocation = resolve(fileName);
        try (OutputStream out = Files.newOutputStream(targetLocation)) {
            content.transferTo(out);
            return fileName;
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    /** Streams the raw bytes stored under {@code fileName}. */
    public Resource load(String fileName) {
        Path filePath = resolve(fileName);
        try {
            return new InputStreamResource(Files.newInputStream(filePath));
        } catch (NoSuchFileException ex) {
            throw new ResourceNotFoundException("File not found: " + fileName);
        } catch (IOException ex) {
            throw new FileStorageException("Could not read file " + fileName, ex);
        }
    }

    /**
     * Removes the stored file.
     *
     * <p>Throws on failure rather than logging and returning. Swallowing it meant the database row
     * was deleted anyway and the encrypted file stayed on disk with nothing left pointing at it —
     * an invisible leak of patient data. Callers order this after their commit, so a failure here
     * is a loud operational problem rather than a corrupted transaction.
     */
    public void delete(String fileName) {
        Path filePath = resolve(fileName);
        try {
            boolean removed = Files.deleteIfExists(filePath);
            log.info("Deleted file {} (existed: {})", fileName, removed);
        } catch (IOException ex) {
            throw new FileStorageException("Could not delete file " + fileName, ex);
        }
    }

    /**
     * Resolves a stored name inside the storage directory, rejecting anything that escapes it.
     *
     * <p>Names are generated server-side today, so traversal is not currently reachable — but this
     * is the one place that turns a name into a filesystem path, so it is the right place to make
     * that guarantee unconditional.
     */
    private Path resolve(String fileName) {
        Path resolved = this.fileStorageLocation.resolve(fileName).normalize();
        if (!resolved.startsWith(this.fileStorageLocation)) {
            throw new FileStorageException("Invalid stored file name: " + fileName);
        }
        return resolved;
    }
}
