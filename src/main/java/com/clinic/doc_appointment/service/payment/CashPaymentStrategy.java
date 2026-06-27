package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentMethod;
import com.clinic.doc_appointment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CashPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.CASH;
    }

    @Override
    public PaymentResult initiate(PaymentContext context) {
        // Cash is collected in person; the record stays PENDING until the clinic confirms receipt.
        return new PaymentResult(PaymentStatus.PENDING, "CASH-" + UUID.randomUUID(),
                "Cash to be collected at the clinic.");
    }
}
