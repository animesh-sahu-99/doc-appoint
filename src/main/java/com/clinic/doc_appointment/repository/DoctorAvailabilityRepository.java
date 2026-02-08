package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, String> {

    // Find available slot by ID
    Optional<DoctorAvailability> findBySlotIdAndIsAvailableTrue(String slotId);

    // Find all available slots for a doctor on a date
    List<DoctorAvailability> findByDoctorDoctorIdAndSlotDateAndIsAvailableTrue(
            String doctorId, LocalDate slotDate);

    // Find all available slots for a doctor
    List<DoctorAvailability> findByDoctorDoctorIdAndIsAvailableTrue(String doctorId);

    // Find available slots in date range
    @Query("SELECT s FROM DoctorAvailability s " +
            "WHERE s.doctor.doctorId = :doctorId " +
            "AND s.slotDate BETWEEN :startDate AND :endDate " +
            "AND s.isAvailable = true " +
            "ORDER BY s.slotDate, s.startTime")
    List<DoctorAvailability> findAvailableSlotsByDoctorAndDateRange(
            @Param("doctorId") String doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}