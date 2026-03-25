package com.clinic.doc_appointment.dto.response;

import com.clinic.doc_appointment.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private String id;
    private String title;
    private String message;
    private NotificationType type;
    private String relatedEntityId;
    private boolean isRead;
    private LocalDateTime createdAt;
}
