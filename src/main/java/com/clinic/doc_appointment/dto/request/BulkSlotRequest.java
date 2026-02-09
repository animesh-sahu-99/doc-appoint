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
    @Min(value = 10, message = "Minimum slot duration is 10 minutes")
    @Max(value = 120, message = "Maximum slot duration is 120 minutes")
    private Integer slotDurationMinutes;  // e.g., 30 minutes

    @Min(value = 0, message = "Break duration cannot be negative")
    @Max(value = 60, message = "Maximum break duration is 60 minutes")
    private Integer breakDurationMinutes = 0;  // Break between slots

    @AssertTrue(message = "Day end time must be after day start time")
    public boolean isDayEndTimeAfterStartTime() {
        if (dayStartTime == null || dayEndTime == null) {
            return true; // Let @NotNull handle null validation
        }
        return dayEndTime.isAfter(dayStartTime);
    }
}
