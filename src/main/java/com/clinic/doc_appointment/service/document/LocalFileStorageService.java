package com.clinic.doc_appointment.service.document;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
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
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    /** Persists the given bytes under {@code fileName} and returns that name. */
    public String store(InputStream content, String fileName) {
        Path targetLocation = this.fileStorageLocation.resolve(fileName);
        try (OutputStream out = new FileOutputStream(targetLocation.toFile())) {
            content.transferTo(out);
            return fileName;
        } catch (Exception ex) {
            throw new RuntimeException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    /** Streams the raw bytes stored under {@code fileName}. */
    public Resource load(String fileName) {
        Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
        if (!Files.exists(filePath)) {
            throw new RuntimeException("File not found " + fileName);
        }
        try {
            return new InputStreamResource(new FileInputStream(filePath.toFile()));
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("File not found " + fileName, ex);
        }
    }

    public void delete(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Files.deleteIfExists(filePath);
            log.info("Deleted file: {}", fileName);
        } catch (Exception ex) {
            log.error("Failed to delete file: " + fileName, ex);
        }
    }
}
