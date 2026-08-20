package com.clinic.doc_appointment.dto.request;

import com.clinic.doc_appointment.enums.Specialization;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DoctorRegistrationRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
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

    @NotNull(message = "Specialization is required")
    private Specialization specialization;

    @Size(max = 255, message = "Qualification must not exceed 255 characters")
    private String qualification;

    @Min(value = 0, message = "Experience cannot be negative")
    private Integer experienceYears;

    @DecimalMin(value = "0.0", message = "Fee must be positive")
    private BigDecimal consultationFee;

    @Size(max = 2000, message = "About must not exceed 2000 characters")
    private String about;

}
