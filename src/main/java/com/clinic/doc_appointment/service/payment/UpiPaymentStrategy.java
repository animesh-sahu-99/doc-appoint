package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentMethod;
import com.clinic.doc_appointment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UpiPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.UPI;
    }

    @Override
    public PaymentResult initiate(PaymentContext context) {
        // TODO: create a UPI collect request via a real PSP. Scaffold returns PENDING.
        return new PaymentResult(PaymentStatus.PENDING, "TXN-" + UUID.randomUUID(),
                "UPI collect request initiated (stub) — awaiting approval.");
    }
}
