package com.clinic.doc_appointment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Partial profile update: every field is optional, and a null (or blank string) means "leave this
 * one alone".
 *
 * <p>Optional is not the same as unconstrained. The bounds here deliberately mirror
 * {@link DoctorRegistrationRequest} — the same columns, so the same rules. Without them the update
 * path accepted a negative consultation fee and negative years of experience that registration
 * would have rejected.
 */
@Data
public class DoctorUpdateRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 255, message = "Qualification must not exceed 255 characters")
    private String qualification;

    @Min(value = 0, message = "Experience cannot be negative")
    private Integer experienceYears;

    @DecimalMin(value = "0.0", message = "Fee must be positive")
    private BigDecimal consultationFee;

    @Size(max = 2000, message = "About must not exceed 2000 characters")
    private String about;
}
