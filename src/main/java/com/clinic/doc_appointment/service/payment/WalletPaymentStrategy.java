package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentMethod;
import com.clinic.doc_appointment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WalletPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.WALLET;
    }

    @Override
    public PaymentResult initiate(PaymentContext context) {
        // TODO: debit the in-app wallet balance. Scaffold returns PENDING.
        return new PaymentResult(PaymentStatus.PENDING, "WALLET-" + UUID.randomUUID(),
                "Wallet debit initiated (stub).");
    }
}
