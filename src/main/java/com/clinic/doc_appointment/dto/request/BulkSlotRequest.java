package com.clinic.doc_appointment.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class BulkSlotRequest {

    @NotNull(message = "Doctor ID is required")
    private String doctorId;

    @NotNull(message = "Slot date is required")
    private LocalDate slotDate;

    @NotNull(message = "Day start time is required")
    private LocalTime dayStartTime;  // e.g., 09:00

    @NotNull(message = "Day end time is required")
    private LocalTime dayEndTime;    // e.g., 17:00

    @NotNull(message = "Slot duration is required")
    private Integer slotDurationMinutes;  // e.g., 30 minutes

    private Integer breakDurationMinutes = 0;  // Break between slots
}
