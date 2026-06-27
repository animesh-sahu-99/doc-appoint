package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentMethod;

/**
 * Strategy for handling one {@link PaymentMethod}. Adding a new method is just adding a bean — the
 * {@link PaymentStrategyFactory} collects them automatically (OCP).
 *
 * <p>These are scaffolds: no real gateway is integrated yet.
 */
public interface PaymentStrategy {

    PaymentMethod getMethod();

    PaymentResult initiate(PaymentContext context);
}
