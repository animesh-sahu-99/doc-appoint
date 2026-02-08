package com.clinic.doc_appointment.dto.request;

import com.clinic.doc_appointment.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientRegistrationRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Country code is required")
    @Pattern(regexp = "^\\+[0-9]{1,4}$", message = "Invalid country code format")
    private String countryCode;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{7,15}$", message = "Phone number must be 7-15 digits")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private Gender gender;
    private LocalDate dateOfBirth;
    private String address;
}
