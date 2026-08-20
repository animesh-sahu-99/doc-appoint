// dto/request/PatientUpdateRequest.java
package com.clinic.doc_appointment.dto.request;

import com.clinic.doc_appointment.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientUpdateRequest {

    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    @Email(message = "Invalid email format")
    private String email;

    private Gender gender;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;
}