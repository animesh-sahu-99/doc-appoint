package com.clinic.doc_appointment.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.clinic.doc_appointment.enums.Gender;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "patients", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"countryCode", "phoneNumber"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Patient {

    @Id
    private String patientId;

    @PrePersist
    public void ensureId(){
        if(patientId == null){
            this.patientId = "PAT-" + UUID.randomUUID();
        }
    }

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    @Column(nullable = true, unique = true)
    @Email
    private String email;

    @Column(name = "countryCode", length = 5, nullable = false)
    private String countryCode; //  +1, +91, etc.

    @Column(name = "phoneNumber", length = 15, nullable = false)
    private String phoneNumber; //  9415050850

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private LocalDate dateOfBirth;

    private String address;

    @JsonManagedReference
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL)
    private List<Appointment> appointments = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
