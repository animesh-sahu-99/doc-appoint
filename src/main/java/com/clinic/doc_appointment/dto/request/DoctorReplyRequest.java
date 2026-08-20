package com.clinic.doc_appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DoctorReplyRequest {
    @NotBlank(message = "Reply cannot be empty")
    @Size(max = 1000, message = "Reply must not exceed 1000 characters")
    private String reply;
}
