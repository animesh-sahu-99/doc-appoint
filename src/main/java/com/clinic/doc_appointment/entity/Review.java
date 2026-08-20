package com.clinic.doc_appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import com.clinic.doc_appointment.enums.IdPrefix;
import com.clinic.doc_appointment.util.IdGenerator;

@Entity
@Table(name = "reviews")
@Check(name = "chk_review_rating", constraints = "rating BETWEEN 1 AND 5")
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
            reviewId = IdGenerator.withPrefix(IdPrefix.REVIEW);
        }
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", unique = true, nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
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
