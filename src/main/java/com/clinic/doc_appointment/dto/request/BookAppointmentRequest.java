package com.clinic.doc_appointment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookAppointmentRequest {

    @NotNull(message = "Patient ID is required")
    private String patientId;

    @NotNull(message = "Slot ID is required")
    private String slotId;

    private String reasonForVisit;
    private String notes;
}
