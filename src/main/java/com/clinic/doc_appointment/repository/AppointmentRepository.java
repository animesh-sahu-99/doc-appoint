// repository/AppointmentRepository.java
package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, String> {

    Optional<Appointment> findByAppointmentNumber(String appointmentNumber);

    List<Appointment> findByPatientPatientId(String patientId);

    List<Appointment> findByDoctorDoctorId(String doctorId);

    List<Appointment> findByPatientPatientIdAndStatus(String patientId, AppointmentStatus status);

    List<Appointment> findByDoctorDoctorIdAndStatus(String doctorId, AppointmentStatus status);

    @Query("SELECT a FROM Appointment a " +
            "WHERE a.patient.patientId = :patientId " +
            "ORDER BY a.slot.slotDate DESC, a.slot.startTime DESC")
    List<Appointment> findByPatientOrderByDateDesc(@Param("patientId") String patientId);

    @Query("SELECT a FROM Appointment a " +
            "WHERE a.doctor.doctorId = :doctorId " +
            "AND a.slot.slotDate = :date " +
            "ORDER BY a.slot.startTime")
    List<Appointment> findByDoctorAndDate(
            @Param("doctorId") String doctorId,
            @Param("date") LocalDate date);

    @Query("SELECT a FROM Appointment a " +
            "WHERE a.doctor.doctorId = :doctorId " +
            "AND a.slot.slotDate >= CURRENT_DATE " +
            "AND a.status IN ('PENDING', 'CONFIRMED') " +
            "ORDER BY a.slot.slotDate, a.slot.startTime")
    List<Appointment> findUpcomingByDoctor(@Param("doctorId") String doctorId);

    @Query("SELECT a FROM Appointment a " +
            "WHERE a.patient.patientId = :patientId " +
            "AND a.slot.slotDate >= CURRENT_DATE " +
            "AND a.status IN ('PENDING', 'CONFIRMED') " +
            "ORDER BY a.slot.slotDate, a.slot.startTime")
    List<Appointment> findUpcomingByPatient(@Param("patientId") String patientId);
}