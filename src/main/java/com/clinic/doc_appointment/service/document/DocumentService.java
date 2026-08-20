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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

        // Written before the row is committed, so a later rollback would leave the ciphertext on
        // disk with no row pointing at it. Registering a compensating unlink keeps the two in step.
        String storedFileName = fileStorageService.storeFile(file);
        deleteFileAfterRollback(storedFileName);

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

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsForAppointment(String appointmentId, String userId, Role role) {
        Appointment appointment = EntityFinder.findOrThrow(appointmentRepository, appointmentId, "Appointment not found");

        validateAccess(appointment, userId, role);

        return documentMapper.toResponseList(
                documentRepository.findByAppointmentAppointmentIdOrderByCreatedAtDesc(appointmentId));
    }

    /**
     * Removes a document and its ciphertext.
     *
     * <p>Two things were wrong here. The permission test was {@code role != Role.DOCTOR}, a check on
     * what kind of user the caller is rather than on whether this document is theirs — so any doctor
     * in the system could destroy any patient's medical record. And the file was unlinked
     * <em>before</em> the row was deleted, inside the transaction, so a rollback left a row pointing
     * at a file that no longer existed.
     *
     * <p>Now: the appointment-level access check applies as it does everywhere else in this class,
     * the uploader rule narrows it further, the row goes first, and the irreversible unlink happens
     * only once the transaction has actually committed.
     */
    @Transactional
    public void deleteDocument(String documentId, String userId, Role role) {
        AppointmentDocument doc = EntityFinder.findOrThrow(documentRepository, documentId, "Document not found");

        validateAccess(doc.getAppointment(), userId, role);

        // Within an appointment, a document may be removed by whoever uploaded it, or by the
        // appointment's doctor (who validateAccess has already confirmed is this appointment's own).
        boolean isUploader = doc.getUploaderId().equals(userId);
        if (!isUploader && role != Role.DOCTOR) {
            throw new ForbiddenOperationException("You do not have permission to delete this file");
        }

        documentRepository.delete(doc);
        deleteFileAfterCommit(doc.getFileUrl());

        log.info("Document {} deleted successfully by {}", documentId, userId);
    }

    /**
     * Single DB call: validates access, returns both metadata and the decrypted resource stream.
     */
    @Transactional(readOnly = true)
    public DocumentDownload downloadDocument(String documentId, String userId, Role role) {
        AppointmentDocument doc = EntityFinder.findOrThrow(documentRepository, documentId, "Document not found");

        validateAccess(doc.getAppointment(), userId, role);

        Resource resource = fileStorageService.loadFileAsResource(doc.getFileUrl());
        return new DocumentDownload(doc, resource);
    }

    /** Holds metadata + decrypted stream together to avoid a second DB round-trip. */
    public record DocumentDownload(AppointmentDocument metadata, Resource resource) {}

    /**
     * Only the appointment's own patient or its own doctor may touch its documents.
     *
     * <p>Written as an allow-list ending in a throw, not as a pair of {@code if (wrong) throw}
     * guards. The previous form fell through to "permitted" for any role that was neither PATIENT
     * nor DOCTOR, so the moment a third role existed it would have silently gained access to every
     * patient's medical documents.
     */
    private void validateAccess(Appointment appointment, String userId, Role role) {
        if (role == Role.PATIENT && appointment.getPatient().getPatientId().equals(userId)) {
            return;
        }
        if (role == Role.DOCTOR && appointment.getDoctor().getDoctorId().equals(userId)) {
            return;
        }
        throw new ForbiddenOperationException("Access Denied: this appointment does not belong to you.");
    }

    /** Unlinks the file once the surrounding transaction commits — never before. */
    private void deleteFileAfterCommit(String storedFileName) {
        runAfterCompletion(storedFileName, TransactionSynchronization.STATUS_COMMITTED);
    }

    /** Unlinks an orphaned upload if the surrounding transaction rolls back. */
    private void deleteFileAfterRollback(String storedFileName) {
        runAfterCompletion(storedFileName, TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    private void runAfterCompletion(String storedFileName, int onStatus) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No transaction to wait for (a direct call outside the web flow): act immediately.
            tryDelete(storedFileName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == onStatus) {
                    tryDelete(storedFileName);
                }
            }
        });
    }

    /**
     * A failure here cannot be propagated — the transaction is already over — so it is logged at
     * ERROR. That leaves an orphaned file, which is recoverable; throwing would leave the caller
     * with a failed response for work that actually succeeded, which is not.
     */
    private void tryDelete(String storedFileName) {
        try {
            fileStorageService.deleteFile(storedFileName);
        } catch (RuntimeException ex) {
            log.error("Orphaned stored file {} could not be removed; it needs manual cleanup", storedFileName, ex);
        }
    }
}
