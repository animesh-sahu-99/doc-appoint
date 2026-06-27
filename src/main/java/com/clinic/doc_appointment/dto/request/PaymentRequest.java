package com.clinic.doc_appointment.dto.request;

import com.clinic.doc_appointment.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequest {

    @NotBlank(message = "Appointment ID is required")
    private String appointmentId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
}
