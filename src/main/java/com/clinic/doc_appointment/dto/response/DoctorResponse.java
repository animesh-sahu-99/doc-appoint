package com.clinic.doc_appointment.dto.response;

import com.clinic.doc_appointment.enums.Specialization;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class DoctorResponse {
    private String doctorId;
    private String name;
    private String email;
    private String phone;
    private Specialization specialization;
    private String specializationDisplayName;  // For frontend display
    private String specializationDescription;
    private String qualification;
    private Integer experienceYears;
    private BigDecimal consultationFee;
    private String profileImage;
    private String about;
    private Double averageRating;
    private Integer totalReviews;
    private Boolean isActive;

}
