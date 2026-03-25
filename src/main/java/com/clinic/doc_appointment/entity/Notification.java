package com.clinic.doc_appointment.entity;

import com.clinic.doc_appointment.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_user_read", columnList = "user_id, is_read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Notification {

    @Id
    @Column(name = "notification_id")
    private String id;

    @PrePersist
    public void ensureId() {
        if (id == null) {
            this.id = "NOT-" + UUID.randomUUID();
        }
    }

    // This can store either a patientId (PAT-...) or doctorId (DOC-...)
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    // Optional: ID of related entity (e.g. appointmentId) for deep linking
    @Column(name = "related_entity_id")
    private String relatedEntityId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
