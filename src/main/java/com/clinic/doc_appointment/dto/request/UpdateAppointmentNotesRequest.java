package com.clinic.doc_appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentNotesRequest {
    @NotBlank(message = "Notes cannot be empty.")
    private String notes;
}
