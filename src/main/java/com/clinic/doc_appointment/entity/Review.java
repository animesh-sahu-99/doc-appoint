package com.clinic.doc_appointment.entity;

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
@Table(name = "reviews")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class Review {
    @Id
    private String reviewId;

    @PrePersist
    public void generateId() {
        if (reviewId == null) {
            reviewId = "REVIEW-" + UUID.randomUUID();
        }
    }

    @OneToOne
    @JoinColumn(name = "appointment_id", unique = true, nullable = false)
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false)
    private Integer rating; // 1 to 5

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(columnDefinition = "TEXT")
    private String doctorReply;

    private LocalDateTime repliedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
