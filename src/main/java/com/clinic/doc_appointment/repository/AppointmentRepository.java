package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, String> {

    Optional<Appointment> findByAppointmentNumber(String appointmentNumber);

    List<Appointment> findByPatientPatientId(String patientId);

    List<Appointment> findByDoctorDoctorId(String doctorId);

    List<Appointment> findByPatientPatientIdAndStatus(String patientId, AppointmentStatus status);

    List<Appointment> findByDoctorDoctorIdAndStatus(String doctorId, AppointmentStatus status);
}
