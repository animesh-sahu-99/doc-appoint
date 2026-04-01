package com.clinic.doc_appointment.service.document;

import com.clinic.doc_appointment.dto.response.DocumentResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.AppointmentDocument;
import com.clinic.doc_appointment.exception.InvalidStateException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.repository.AppointmentDocumentRepository;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final AppointmentDocumentRepository documentRepository;
    private final AppointmentRepository appointmentRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public DocumentResponse uploadDocument(String appointmentId, String userId, String role, MultipartFile file, String documentType) {
        log.info("User {} ({}) uploading {} to appointment {}", userId, role, documentType, appointmentId);

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        validateAccess(appointment, userId, role);

        // Store file securely (encrypted local)
        String storedFileName = fileStorageService.storeFile(file);

        AppointmentDocument doc = new AppointmentDocument()
                .setAppointment(appointment)
                .setUploaderId(userId)
                .setUploaderRole(role.toUpperCase())
                .setFileName(file.getOriginalFilename())
                .setFileUrl(storedFileName) // It's just the physical name locally
                .setFileType(file.getContentType())
                .setDocumentType(documentType.toUpperCase())
                .setFileSize(file.getSize());

        doc = documentRepository.save(doc);

        return mapToResponse(doc);
    }

    public List<DocumentResponse> getDocumentsForAppointment(String appointmentId, String userId, String role) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        validateAccess(appointment, userId, role);

        return documentRepository.findByAppointmentAppointmentIdOrderByCreatedAtDesc(appointmentId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteDocument(String documentId, String userId, String role) {
        AppointmentDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Only the actual uploader or the Doctor can delete
        if (!doc.getUploaderId().equals(userId) && !role.equalsIgnoreCase("DOCTOR")) {
             throw new InvalidStateException("You do not have permission to delete this file");
        }

        // Delete the physical encrypted file
        fileStorageService.deleteFile(doc.getFileUrl());
        
        // Remove DB reference
        documentRepository.delete(doc);
        log.info("Document {} deleted successfully by {}", documentId, userId);
    }

    public Resource downloadDocumentAsResource(String documentId, String userId, String role) {
        AppointmentDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
                
        // ensure requester is authorized
        validateAccess(doc.getAppointment(), userId, role);

        return fileStorageService.loadFileAsResource(doc.getFileUrl());
    }
    
    public AppointmentDocument getDocumentMetadata(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private void validateAccess(Appointment appointment, String userId, String role) {
        // Patient check
        if (role.equalsIgnoreCase("PATIENT") && !appointment.getPatient().getPatientId().equals(userId)) {
            throw new InvalidStateException("Access Denied: Appointment does not belong to this patient.");
        }
        // Doctor check
        if (role.equalsIgnoreCase("DOCTOR") && !appointment.getDoctor().getDoctorId().equals(userId)) {
            throw new InvalidStateException("Access Denied: Appointment does not belong to this doctor.");
        }
    }

    private DocumentResponse mapToResponse(AppointmentDocument doc) {
        // Create full URL to hit the controller download endpoint
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/documents/")
                .path(doc.getDocumentId())
                .path("/download")
                .toUriString();

        return DocumentResponse.builder()
                .documentId(doc.getDocumentId())
                .appointmentId(doc.getAppointment().getAppointmentId())
                .uploaderId(doc.getUploaderId())
                .uploaderRole(doc.getUploaderRole())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .documentType(doc.getDocumentType())
                .fileSize(doc.getFileSize())
                .downloadUrl(fileDownloadUri)
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
