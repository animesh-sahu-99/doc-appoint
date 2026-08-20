// repository/AppointmentRepository.java
package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Appointment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Appointments.
 *
 * <p>{@code AppointmentMapper} reads {@code patient}, {@code doctor} and {@code slot} for every row
 * it renders, and those associations are LAZY. Each read path therefore declares an
 * {@link EntityGraph} so the three arrive with the query instead of costing a round-trip per row —
 * previously the associations were EAGER-by-default, which produced the same N+1 invisibly and
 * unconditionally, including on paths that never touched them.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, String> {

    @Override
    @EntityGraph(attributePaths = {"patient", "doctor", "slot"})
    Optional<Appointment> findById(String appointmentId);

    @EntityGraph(attributePaths = {"patient", "doctor", "slot"})
    Optional<Appointment> findByAppointmentNumber(String appointmentNumber);

    @EntityGraph(attributePaths = {"patient", "doctor", "slot"})
    List<Appointment> findByDoctorDoctorId(String doctorId);

    /** True if the patient has any appointment at all — the gate on hard-deleting the account. */
    boolean existsByPatientPatientId(String patientId);

    /** True if the given doctor has at least one appointment with the given patient. */
    boolean existsByDoctorDoctorIdAndPatientPatientId(String doctorId, String patientId);

    @EntityGraph(attributePaths = {"patient", "doctor", "slot"})
    @Query("SELECT a FROM Appointment a " +
            "WHERE a.patient.patientId = :patientId " +
            "ORDER BY a.slot.slotDate DESC, a.slot.startTime DESC")
    List<Appointment> findByPatientOrderByDateDesc(@Param("patientId") String patientId);

    @EntityGraph(attributePaths = {"patient", "doctor", "slot"})
    @Query("SELECT a FROM Appointment a " +
            "WHERE a.doctor.doctorId = :doctorId " +
            "AND a.slot.slotDate = :date " +
            "ORDER BY a.slot.startTime")
    List<Appointment> findByDoctorAndDate(
            @Param("doctorId") String doctorId,
            @Param("date") LocalDate date);

    @EntityGraph(attributePaths = {"patient", "doctor", "slot"})
    @Query("SELECT a FROM Appointment a " +
            "WHERE a.doctor.doctorId = :doctorId " +
            "AND a.slot.slotDate >= CURRENT_DATE " +
            "AND (a.status = com.clinic.doc_appointment.enums.AppointmentStatus.PENDING " +
            "  OR a.status = com.clinic.doc_appointment.enums.AppointmentStatus.CONFIRMED) " +
            "ORDER BY a.slot.slotDate, a.slot.startTime")
    List<Appointment> findUpcomingByDoctor(@Param("doctorId") String doctorId);

    @EntityGraph(attributePaths = {"patient", "doctor", "slot"})
    @Query("SELECT a FROM Appointment a " +
            "WHERE a.patient.patientId = :patientId " +
            "AND a.slot.slotDate >= CURRENT_DATE " +
            "AND (a.status = com.clinic.doc_appointment.enums.AppointmentStatus.PENDING " +
            "  OR a.status = com.clinic.doc_appointment.enums.AppointmentStatus.CONFIRMED) " +
            "ORDER BY a.slot.slotDate, a.slot.startTime")
    List<Appointment> findUpcomingByPatient(@Param("patientId") String patientId);
}