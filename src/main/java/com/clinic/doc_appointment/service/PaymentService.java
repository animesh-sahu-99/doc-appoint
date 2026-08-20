package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.PaymentRequest;
import com.clinic.doc_appointment.dto.response.PaymentResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Payment;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.repository.PaymentRepository;
import com.clinic.doc_appointment.security.AppointmentAccessGuard;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.payment.PaymentContext;
import com.clinic.doc_appointment.service.payment.PaymentResult;
import com.clinic.doc_appointment.service.payment.PaymentStrategy;
import com.clinic.doc_appointment.service.payment.PaymentStrategyFactory;
import com.clinic.doc_appointment.exception.InvalidStateException;
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
    private final AppointmentAccessGuard accessGuard;

    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request, UserPrincipal caller) {
        Appointment appointment = EntityFinder.findOrThrow(appointmentRepository,
                request.getAppointmentId(), "Appointment not found with ID: " + request.getAppointmentId());

        // Only the patient the appointment belongs to may pay for it. Without this any signed-in
        // user could raise a payment row against a stranger's consultation.
        accessGuard.assertOwnsAppointmentAsPatient(caller, appointment);

        // Payment is one-to-one with Appointment, so a second attempt would fail on a unique key and
        // surface as an unexplained conflict.
        if (paymentRepository.findByAppointmentAppointmentId(appointment.getAppointmentId()).isPresent()) {
            throw new InvalidStateException("A payment has already been initiated for this appointment.");
        }

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

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForAppointment(String appointmentId, UserPrincipal caller) {
        Appointment appointment = EntityFinder.findOrThrow(appointmentRepository, appointmentId,
                "Appointment not found with ID: " + appointmentId);
        accessGuard.assertCanViewAppointment(caller, appointment);

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
