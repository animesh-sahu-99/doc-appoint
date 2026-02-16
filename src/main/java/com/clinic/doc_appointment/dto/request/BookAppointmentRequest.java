// dto/request/BookAppointmentRequest.java
package com.clinic.doc_appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookAppointmentRequest {

    @NotBlank(message = "Patient ID is required")
    private String patientId;

    @NotBlank(message = "Slot ID is required")
    private String slotId;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reasonForVisit;

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;
}