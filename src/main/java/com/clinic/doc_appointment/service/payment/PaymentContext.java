package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentMethod;

import java.math.BigDecimal;

/** Input to a {@link PaymentStrategy}: what to charge, for which appointment, by which method. */
public record PaymentContext(String appointmentId, BigDecimal amount, PaymentMethod paymentMethod) {
}
