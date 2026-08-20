package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.AppointmentDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentDocumentRepository extends JpaRepository<AppointmentDocument, String> {
    /**
     * Fetches the owning appointment and both parties: every caller runs an access check that
     * compares them against the principal.
     */
    @Override
    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor"})
    Optional<AppointmentDocument> findById(String documentId);

    List<AppointmentDocument> findByAppointmentAppointmentIdOrderByCreatedAtDesc(String appointmentId);

    /** True if any document is attached to an appointment of this patient. */
    boolean existsByAppointmentPatientPatientId(String patientId);
}
