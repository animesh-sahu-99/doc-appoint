package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByAppointmentAppointmentId(String appointmentId);
    Optional<Payment> findByTransactionId(String transactionId);
}