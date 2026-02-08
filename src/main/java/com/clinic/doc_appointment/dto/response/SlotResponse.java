package com.clinic.doc_appointment.dto.response;

import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalTime;

@Data @Setter
@Accessors(chain = true)
public class SlotResponse {
    private String slotId;
    private String doctorId;
    private String doctorName;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer durationMinutes;
    private Boolean isAvailable;
}
