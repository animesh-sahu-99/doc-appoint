package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentMethod;
import com.clinic.doc_appointment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CardPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.CARD;
    }

    @Override
    public PaymentResult initiate(PaymentContext context) {
        // TODO: integrate a real card gateway (e.g. Stripe/Razorpay). Scaffold returns PENDING.
        return new PaymentResult(PaymentStatus.PENDING, "TXN-" + UUID.randomUUID(),
                "Card payment initiated (stub) — awaiting gateway confirmation.");
    }
}
