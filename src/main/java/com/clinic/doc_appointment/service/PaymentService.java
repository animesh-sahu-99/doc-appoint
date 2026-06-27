package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.PaymentRequest;
import com.clinic.doc_appointment.dto.response.PaymentResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Payment;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.repository.PaymentRepository;
import com.clinic.doc_appointment.service.payment.PaymentContext;
import com.clinic.doc_appointment.service.payment.PaymentResult;
import com.clinic.doc_appointment.service.payment.PaymentStrategy;
import com.clinic.doc_appointment.service.payment.PaymentStrategyFactory;
import com.clinic.doc_appointment.util.EntityFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Coordinates payment for an appointment via the {@link PaymentStrategy} for the chosen method.
 *
 * <p>Scaffold: creates a {@code PENDING} {@link Payment} row; no real gateway is contacted, and the
 * amount is driven by the doctor's {@code consultationFee}. Booking itself does not create payments —
 * this is a separate, explicit step (the delicate {@code bookAppointment} retry path is untouched).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStrategyFactory paymentStrategyFactory;

    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        Appointment appointment = EntityFinder.findOrThrow(appointmentRepository,
                request.getAppointmentId(), "Appointment not found with ID: " + request.getAppointmentId());

        BigDecimal amount = appointment.getDoctor().getConsultationFee();

        PaymentStrategy strategy = paymentStrategyFactory.resolve(request.getPaymentMethod());
        PaymentResult result = strategy.initiate(
                new PaymentContext(appointment.getAppointmentId(), amount, request.getPaymentMethod()));

        Payment payment = new Payment()
                .setAppointment(appointment)
                .setAmount(amount)
                .setPaymentMethod(request.getPaymentMethod())
                .setTransactionId(result.transactionId())
                .setStatus(result.status());

        Payment saved = paymentRepository.save(payment);
        log.info("Initiated {} payment {} for appointment {} — status {}",
                request.getPaymentMethod(), saved.getPaymentId(), appointment.getAppointmentId(), saved.getStatus());

        return toResponse(saved, result.message());
    }

    public PaymentResponse getPaymentForAppointment(String appointmentId) {
        Payment payment = EntityFinder.orThrow(
                paymentRepository.findByAppointmentAppointmentId(appointmentId),
                "Payment not found for appointment: " + appointmentId);
        return toResponse(payment, null);
    }

    private PaymentResponse toResponse(Payment payment, String message) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .appointmentId(payment.getAppointment().getAppointmentId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .status(payment.getStatus())
                .message(message)
                .paymentDate(payment.getPaymentDate())
                .build();
    }
}
