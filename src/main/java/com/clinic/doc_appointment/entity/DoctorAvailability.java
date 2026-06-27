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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.clinic.doc_appointment.enums.IdPrefix;
import com.clinic.doc_appointment.util.IdGenerator;

@Entity
@Table(name = "doctor_availability")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAvailability {
    @Id
    private String slotId;

    @PrePersist
    public void generateId(){
        if(slotId == null){
            slotId = IdGenerator.withPrefix(IdPrefix.SLOT);
        }
    }

    // OPTIMISTIC LOCKING - Version field
    @Version
    private Long version;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false )
    @JsonBackReference
    private Doctor doctor;

    @Column(nullable = false)
    private LocalDate slotDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private Integer durationMinutes;  // Variable duration: 15, 30, 45, 60 mins etc.

    @Column(nullable = false)
    private Boolean isAvailable = true;

    @OneToOne(mappedBy = "slot")
    private Appointment appointment;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;


}
