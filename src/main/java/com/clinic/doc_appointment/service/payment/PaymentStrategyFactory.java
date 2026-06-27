package com.clinic.doc_appointment.service.payment;

import com.clinic.doc_appointment.enums.PaymentMethod;
import com.clinic.doc_appointment.exception.InvalidStateException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the {@link PaymentStrategy} for a {@link PaymentMethod}. Spring injects every strategy
 * bean, so registering a new method requires no change here (Factory + Strategy = open for extension).
 */
@Component
public class PaymentStrategyFactory {

    private final Map<PaymentMethod, PaymentStrategy> strategies = new EnumMap<>(PaymentMethod.class);

    public PaymentStrategyFactory(List<PaymentStrategy> strategyBeans) {
        strategyBeans.forEach(strategy -> strategies.put(strategy.getMethod(), strategy));
    }

    public PaymentStrategy resolve(PaymentMethod method) {
        PaymentStrategy strategy = strategies.get(method);
        if (strategy == null) {
            throw new InvalidStateException("Unsupported payment method: " + method);
        }
        return strategy;
    }
}
