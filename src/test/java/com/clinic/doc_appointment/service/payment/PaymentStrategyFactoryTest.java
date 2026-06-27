package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentMethod;
import com.clinic.doc_appointment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaymentStrategyFactoryTest {

    private PaymentStrategyFactory factory() {
        List<PaymentStrategy> strategies = List.of(
                new CardPaymentStrategy(),
                new UpiPaymentStrategy(),
                new NetBankingPaymentStrategy(),
                new CashPaymentStrategy(),
                new WalletPaymentStrategy());
        return new PaymentStrategyFactory(strategies);
    }

    @Test
    void resolvesAStrategyForEveryPaymentMethod() {
        PaymentStrategyFactory factory = factory();
        for (PaymentMethod method : PaymentMethod.values()) {
            PaymentStrategy strategy = factory.resolve(method);
            assertEquals(method, strategy.getMethod());
        }
    }

    @Test
    void strategyReturnsPendingWithTransactionId() {
        PaymentResult result = new CardPaymentStrategy()
                .initiate(new PaymentContext("APPOINTMENT-1", new BigDecimal("500"), PaymentMethod.CARD));
        assertEquals(PaymentStatus.PENDING, result.status());
        assertNotNull(result.transactionId());
    }
}
