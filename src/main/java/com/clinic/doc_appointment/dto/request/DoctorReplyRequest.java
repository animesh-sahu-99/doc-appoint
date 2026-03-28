package com.clinic.doc_appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DoctorReplyRequest {
    @NotBlank(message = "Reply cannot be empty")
    private String reply;
}
