package com.clinic.doc_appointment.dto.request;


import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateSlotRequest {

    @NotNull(message = "Doctor ID is required")
    private String doctorId;

    @NotNull(message = "Slot date is required")
    @FutureOrPresent(message = "Slot date must be today or in future")
    private LocalDate slotDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @NotNull(message = "Duration is required")
    @Min(value = 10, message = "Minimum duration is 10 minutes")
    @Max(value = 120, message = "Maximum duration is 120 minutes")
    private Integer durationMinutes;

    @AssertTrue(message = "End time must be after start time")
    public boolean isEndTimeAfterStartTime() {
        if (startTime == null || endTime == null) {
            return true; // Let @NotNull handle null validation
        }
        return endTime.isAfter(startTime);
    }
}
