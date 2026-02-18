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
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AppointmentEntityListener.class)
public class Appointment {
    @Id
    private String appointmentId;

    @PrePersist
    public void generateId(){
        if(appointmentId == null){
            appointmentId = "APPOINTMENT-"+ UUID.randomUUID();
        }
    }

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

    // Transient field to track previous status for lifecycle callbacks
    @Transient
    private AppointmentStatus previousStatus;

    @PostLoad
    public void storePreviousStatus() {
        this.previousStatus = this.status;
    }
}
