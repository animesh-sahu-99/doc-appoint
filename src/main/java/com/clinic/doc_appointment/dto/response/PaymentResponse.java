package com.clinic.doc_appointment.dto.response;

import com.clinic.doc_appointment.enums.PaymentMethod;
import com.clinic.doc_appointment.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private String paymentId;
    private String appointmentId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String transactionId;
    private PaymentStatus status;
    private String message;
    private LocalDateTime paymentDate;
}
