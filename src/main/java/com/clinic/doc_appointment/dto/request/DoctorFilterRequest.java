package com.clinic.doc_appointment.dto.request;

import com.clinic.doc_appointment.enums.Specialization;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Query parameters DTO for the GET /api/doctors/search endpoint.
 * All fields are optional — unset fields are ignored in the filter.
 */
@Data
public class DoctorFilterRequest {

    /** Partial name match (case-insensitive) */
    private String name;

    /** Exact specialization enum match */
    private Specialization specialization;

    /** Minimum consultation fee (inclusive) */
    private BigDecimal minFee;

    /** Maximum consultation fee (inclusive) */
    private BigDecimal maxFee;

    /** Minimum experience in years (inclusive) */
    private Integer minExperience;

    /**
     * When true, only returns doctors who have at least one available slot.
     * Default: false (return all active doctors regardless of slot availability)
     */
    private Boolean availableOnly = false;
}
