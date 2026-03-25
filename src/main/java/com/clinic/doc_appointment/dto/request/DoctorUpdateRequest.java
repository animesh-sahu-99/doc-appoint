package com.clinic.doc_appointment.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DoctorUpdateRequest {
    private String name;
    private String qualification;
    private Integer experienceYears;
    private BigDecimal consultationFee;
    private String about;
}
