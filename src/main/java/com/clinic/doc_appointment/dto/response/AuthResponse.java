package com.clinic.doc_appointment.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private String role;      // ROLE_DOCTOR or ROLE_PATIENT
    private String userId;    // doctorId or patientId
    private String email;
    private String name;
}
