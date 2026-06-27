package com.clinic.doc_appointment.mapper;

import com.clinic.doc_appointment.dto.response.NotificationResponse;
import com.clinic.doc_appointment.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper implements EntityMapper<Notification, NotificationResponse> {

    @Override
    public NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .relatedEntityId(notification.getRelatedEntityId())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
