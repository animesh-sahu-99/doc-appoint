package com.clinic.doc_appointment.service.document;

import com.clinic.doc_appointment.dto.response.DocumentResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.AppointmentDocument;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.ForbiddenOperationException;
import com.clinic.doc_appointment.mapper.DocumentMapper;
import com.clinic.doc_appointment.repository.AppointmentDocumentRepository;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.util.EntityFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final AppointmentDocumentRepository documentRepository;
    private final AppointmentRepository appointmentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentMapper documentMapper;

    @Transactional
    public DocumentResponse uploadDocument(String appointmentId, String userId, Role role, MultipartFile file, String documentType) {
        log.info("User {} ({}) uploading {} to appointment {}", userId, role, documentType, appointmentId);

        Appointment appointment = EntityFinder.findOrThrow(appointmentRepository, appointmentId, "Appointment not found");

        validateAccess(appointment, userId, role);

        // Store file securely (encrypted local)
        String storedFileName = fileStorageService.storeFile(file);

        AppointmentDocument doc = new AppointmentDocument()
                .setAppointment(appointment)
                .setUploaderId(userId)
                .setUploaderRole(role.name())
                .setFileName(file.getOriginalFilename())
                .setFileUrl(storedFileName) // It's just the physical name locally
                .setFileType(file.getContentType())
                .setDocumentType(documentType.toUpperCase())
                .setFileSize(file.getSize());

        doc = documentRepository.save(doc);

        return documentMapper.toResponse(doc);
    }

    public List<DocumentResponse> getDocumentsForAppointment(String appointmentId, String userId, Role role) {
        Appointment appointment = EntityFinder.findOrThrow(appointmentRepository, appointmentId, "Appointment not found");

        validateAccess(appointment, userId, role);

        return documentMapper.toResponseList(
                documentRepository.findByAppointmentAppointmentIdOrderByCreatedAtDesc(appointmentId));
    }

    @Transactional
    public void deleteDocument(String documentId, String userId, Role role) {
        AppointmentDocument doc = EntityFinder.findOrThrow(documentRepository, documentId, "Document not found");

        // Only the actual uploader or the Doctor can delete
        if (!doc.getUploaderId().equals(userId) && role != Role.DOCTOR) {
             throw new ForbiddenOperationException("You do not have permission to delete this file");
        }

        // Delete the physical encrypted file
        fileStorageService.deleteFile(doc.getFileUrl());

        // Remove DB reference
        documentRepository.delete(doc);
        log.info("Document {} deleted successfully by {}", documentId, userId);
    }

    /**
     * Single DB call: validates access, returns both metadata and the decrypted resource stream.
     */
    public DocumentDownload downloadDocument(String documentId, String userId, Role role) {
        AppointmentDocument doc = EntityFinder.findOrThrow(documentRepository, documentId, "Document not found");

        validateAccess(doc.getAppointment(), userId, role);

        Resource resource = fileStorageService.loadFileAsResource(doc.getFileUrl());
        return new DocumentDownload(doc, resource);
    }

    /** Holds metadata + decrypted stream together to avoid a second DB round-trip. */
    public record DocumentDownload(AppointmentDocument metadata, Resource resource) {}

    private void validateAccess(Appointment appointment, String userId, Role role) {
        // Patient check
        if (role == Role.PATIENT && !appointment.getPatient().getPatientId().equals(userId)) {
            throw new ForbiddenOperationException("Access Denied: Appointment does not belong to this patient.");
        }
        // Doctor check
        if (role == Role.DOCTOR && !appointment.getDoctor().getDoctorId().equals(userId)) {
            throw new ForbiddenOperationException("Access Denied: Appointment does not belong to this doctor.");
        }
    }
}
