package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentMethod;
import com.clinic.doc_appointment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NetBankingPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.NET_BANKING;
    }

    @Override
    public PaymentResult initiate(PaymentContext context) {
        // TODO: redirect to the bank's net-banking page via a real gateway. Scaffold returns PENDING.
        return new PaymentResult(PaymentStatus.PENDING, "TXN-" + UUID.randomUUID(),
                "Net-banking payment initiated (stub) — awaiting bank confirmation.");
    }
}
