package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.AppointmentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentDocumentRepository extends JpaRepository<AppointmentDocument, String> {
    List<AppointmentDocument> findByAppointmentAppointmentIdOrderByCreatedAtDesc(String appointmentId);
}
