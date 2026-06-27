package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentStatus;

/** Outcome of a {@link PaymentStrategy} attempt. */
public record PaymentResult(PaymentStatus status, String transactionId, String message) {
}
