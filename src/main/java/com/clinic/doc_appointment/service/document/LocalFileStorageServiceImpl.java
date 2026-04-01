package com.clinic.doc_appointment.service.document;

import com.clinic.doc_appointment.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final Path fileStorageLocation;
    private final CryptoUtils cryptoUtils;

    public LocalFileStorageServiceImpl(@Value("${file.upload-dir:uploads}") String uploadDir, CryptoUtils cryptoUtils) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.cryptoUtils = cryptoUtils;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        
        // Generate a unique file name to avoid collisions
        String fileName = UUID.randomUUID().toString() + extension + ".enc";
        Path targetLocation = this.fileStorageLocation.resolve(fileName);

        // Encrypt and save the file
        try (InputStream in = file.getInputStream();
             OutputStream fileOut = new FileOutputStream(targetLocation.toFile());
             OutputStream encOut = cryptoUtils.getEncryptedOutputStream(fileOut)) {
            
            in.transferTo(encOut);
            log.info("Successfully encrypted and stored file: {}", fileName);
            return fileName;

        } catch (Exception ex) {
            throw new RuntimeException("Could not store and encrypt file " + fileName + ". Please try again!", ex);
        }
    }

    @Override
    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            if (!Files.exists(filePath)) {
                throw new RuntimeException("File not found " + fileName);
            }

            // Provide a stream that decrypts the file on-the-fly
            InputStream fileIn = new FileInputStream(filePath.toFile());
            InputStream decIn = cryptoUtils.getDecryptedInputStream(fileIn);
            
            return new InputStreamResource(decIn);

        } catch (Exception ex) {
            throw new RuntimeException("Could not read file " + fileName, ex);
        }
    }

    @Override
    public void deleteFile(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Files.deleteIfExists(filePath);
            log.info("Deleted encrypted file: {}", fileName);
        } catch (Exception ex) {
            log.error("Failed to delete file: " + fileName, ex);
        }
    }
}
