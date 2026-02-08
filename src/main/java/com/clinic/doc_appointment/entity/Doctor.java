package com.clinic.doc_appointment.entity;

import com.clinic.doc_appointment.enums.Specialization;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "doctors", uniqueConstraints = {
@UniqueConstraint(columnNames = {"countryCode", "phoneNumber"})
})
@Getter
@Setter
@Accessors(chain = true) @NoArgsConstructor
@AllArgsConstructor
public class Doctor {
    @Id
    private String doctorId;

    @PrePersist
    public void generateId(){
        if(doctorId == null){
            doctorId = "DOC-"+ UUID.randomUUID();
        }
    }

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = true)
    private String lastName;


    @Column(name = "countryCode", length = 5, nullable = false)
    private String countryCode; //  +1, +91, etc.

    @Column(name = "phoneNumber", length = 15, nullable = false)
    private String phoneNumber; //  9415050850

    @Column(nullable = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Specialization specialization;

    private String qualification;

    private Integer experienceYears;

    @Column(precision = 10, scale = 2)
    private BigDecimal consultationFee;

    private String profileImage;

    @Column(columnDefinition = "TEXT")
    private String about;

    private Boolean isActive = true;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL)
    private List<DoctorAvailability> availabilitySlots = new ArrayList<>();

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL)
    private List<Appointment> appointments = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
