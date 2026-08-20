package com.clinic.doc_appointment.dto.request;

import com.clinic.doc_appointment.enums.Specialization;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Query parameters DTO for the GET /api/doctors/search endpoint.
 * All fields are optional — unset fields are ignored in the filter.
 */
@Data
public class DoctorFilterRequest {

    /** Partial name match (case-insensitive) */
    @Size(max = 100, message = "Name filter must not exceed 100 characters")
    private String name;

    /** Exact specialization enum match */
    private Specialization specialization;

    /** Minimum consultation fee (inclusive) */
    @DecimalMin(value = "0.0", message = "minFee cannot be negative")
    private BigDecimal minFee;

    /** Maximum consultation fee (inclusive) */
    @DecimalMin(value = "0.0", message = "maxFee cannot be negative")
    private BigDecimal maxFee;

    /** Minimum experience in years (inclusive) */
    @Min(value = 0, message = "minExperience cannot be negative")
    private Integer minExperience;

    /** Minimum average rating (inclusive, e.g., 4.0) */
    @DecimalMin(value = "0.0", message = "minRating cannot be negative")
    @DecimalMax(value = "5.0", message = "minRating cannot exceed 5.0")
    private Double minRating;

    /**
     * When true, only returns doctors who have at least one available slot.
     * Default: false (return all active doctors regardless of slot availability)
     */
    private Boolean availableOnly = false;
}
