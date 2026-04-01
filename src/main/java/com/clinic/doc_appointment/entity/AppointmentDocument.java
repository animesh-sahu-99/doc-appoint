package com.clinic.doc_appointment.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointment_documents")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDocument {

    @Id
    private String documentId;

    @PrePersist
    public void generateId() {
        if (documentId == null) {
            documentId = "DOC-" + UUID.randomUUID();
        }
    }

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(nullable = false)
    private String uploaderId; // The ID of the doctor or patient who uploaded it

    @Column(nullable = false)
    private String uploaderRole; // "PATIENT" or "DOCTOR"

    @Column(nullable = false)
    private String fileName; // The original name of the file

    @Column(nullable = false)
    private String fileUrl; // The path where it is stored locally

    @Column(nullable = false)
    private String fileType; // application/pdf, image/jpeg, etc.

    @Column(nullable = false)
    private String documentType; // "REPORT", "PRESCRIPTION", "OTHER"

    private Long fileSize; // Size in bytes

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
