package com.clinic.doc_appointment.entity;

import com.clinic.doc_appointment.enums.AppointmentStatus;
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
import com.clinic.doc_appointment.enums.IdPrefix;
import com.clinic.doc_appointment.util.IdGenerator;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {
    @Id
    private String appointmentId;

    @PrePersist
    public void generateId(){
        if(appointmentId == null){
            appointmentId = IdGenerator.withPrefix(IdPrefix.APPOINTMENT);
        }
    }

    // Optimistic locking - prevents concurrent edits (status/notes) from silently clobbering each other.
    // Works with the @Retryable handlers in AppointmentService for confirm/cancel/complete/notes/no-show.
    @Version
    private Long version;

    // Unique appointment reference number
    @Column(unique = true, nullable = false)
    private String appointmentNumber;

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @OneToOne
    @JoinColumn(name = "slot_id", nullable = false)
    private DoctorAvailability slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    private String reasonForVisit;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToOne(mappedBy = "appointment", cascade = CascadeType.ALL)
    private Payment payment;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
