package com.clinic.doc_appointment.mapper;

import com.clinic.doc_appointment.dto.response.DocumentResponse;
import com.clinic.doc_appointment.entity.AppointmentDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class DocumentMapper implements EntityMapper<AppointmentDocument, DocumentResponse> {

    @Override
    public DocumentResponse toResponse(AppointmentDocument doc) {
        // Build the full URL that hits the controller download endpoint (unchanged behavior).
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
