package com.clinic.doc_appointment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentResponse {
    private String documentId;
    private String appointmentId;
    private String uploaderId;
    private String uploaderRole;
    private String fileName;
    private String fileType;
    private String documentType;
    private Long fileSize;
    private String downloadUrl; // URL for the mobile app to hit to get the stream
    private LocalDateTime createdAt;
}
