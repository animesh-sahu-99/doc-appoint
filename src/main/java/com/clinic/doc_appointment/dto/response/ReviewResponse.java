package com.clinic.doc_appointment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewResponse {
    private String reviewId;
    private String appointmentId;
    private String patientName;
    private Integer rating;
    private String comment;
    private String doctorReply;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
}
