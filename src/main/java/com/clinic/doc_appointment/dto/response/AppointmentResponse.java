package com.clinic.doc_appointment.dto.response;

import com.clinic.doc_appointment.enums.AppointmentStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Accessors(chain = true)
public class AppointmentResponse {
    private String appointmentId;
    private String appointmentNumber;

    // Patient Info
    private String patientId;
    private String patientName;
    private String patientPhone;

    // Doctor Info
    private String doctorId;
    private String doctorName;
    private String specialization;
    private BigDecimal consultationFee;

    // Slot Info
    private String slotId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer durationMinutes;

    private AppointmentStatus status;
    private String reasonForVisit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String notes;
}
