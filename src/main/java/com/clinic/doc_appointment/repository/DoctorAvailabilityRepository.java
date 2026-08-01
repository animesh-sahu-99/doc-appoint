// repository/DoctorAvailabilityRepository.java
package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.DoctorAvailability;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, String> {

    // Find available slot by ID
    Optional<DoctorAvailability> findBySlotIdAndIsAvailableTrue(String slotId);

    // Find all slots for a doctor on a specific date
    List<DoctorAvailability> findByDoctorDoctorIdAndSlotDate(String doctorId, LocalDate slotDate);

    // Find available slots for a doctor on a specific date
    List<DoctorAvailability> findByDoctorDoctorIdAndSlotDateAndIsAvailableTrue(
            String doctorId, LocalDate slotDate);

    // Find available slots for a doctor (future dates only)
    @Query("SELECT s FROM DoctorAvailability s " +
            "WHERE s.doctor.doctorId = :doctorId " +
            "AND s.isAvailable = true " +
            "AND s.slotDate >= CURRENT_DATE " +
            "ORDER BY s.slotDate, s.startTime")
    List<DoctorAvailability> findAvailableSlotsByDoctor(@Param("doctorId") String doctorId);

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

    // Check for overlapping slots
    @Query("SELECT s FROM DoctorAvailability s " +
            "WHERE s.doctor.doctorId = :doctorId " +
            "AND s.slotDate = :date " +
            "AND ((s.startTime < :endTime AND s.endTime > :startTime))")
    List<DoctorAvailability> findOverlappingSlots(
            @Param("doctorId") String doctorId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    // Lock the doctor's slots for a date (SELECT ... FOR UPDATE) so a concurrent booking
    // cannot flip availability between a check and a bulk delete (TOCTOU guard).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DoctorAvailability s " +
            "WHERE s.doctor.doctorId = :doctorId AND s.slotDate = :date")
    List<DoctorAvailability> findByDoctorAndDateForUpdate(
            @Param("doctorId") String doctorId,
            @Param("date") LocalDate date);
}