// dto/response/PatientResponse.java
package com.clinic.doc_appointment.dto.response;

import com.clinic.doc_appointment.enums.Gender;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PatientResponse {

    private String patientId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String countryCode;
    private String phoneNumber;
    private String fullPhoneNumber;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String address;
    private LocalDateTime createdAt;
}