// repository/DoctorAvailabilityRepository.java
package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.DoctorAvailability;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Availability slots.
 *
 * <p><strong>"Bookable" is not the same as "available".</strong> {@code isAvailable} only says the
 * slot is unclaimed; it says nothing about whether the slot has already happened. Cancelling an old
 * appointment sets the flag back to true, and slots are never swept, so any query that offers slots
 * to a client must also exclude ones whose start time has passed — otherwise the client is handed a
 * consultation in the past and booking it fails at the service layer.
 *
 * <p>The cutoff is passed in as {@code today}/{@code nowTime} rather than using {@code CURRENT_DATE}
 * so the same queries are deterministic under test.
 */
@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, String> {

    /** {@code SlotMapper} renders the doctor's name, so every mapped read fetches the doctor. */
    @Override
    @EntityGraph(attributePaths = {"doctor"})
    Optional<DoctorAvailability> findById(String slotId);

    /** Every slot for a doctor on a date, past and booked included — the doctor's own schedule view. */
    @EntityGraph(attributePaths = {"doctor"})
    List<DoctorAvailability> findByDoctorDoctorIdAndSlotDate(String doctorId, LocalDate slotDate);

    /** Unclaimed, not-yet-started slots for a doctor on one date. */
    @EntityGraph(attributePaths = {"doctor"})
    @Query("SELECT s FROM DoctorAvailability s " +
            "WHERE s.doctor.doctorId = :doctorId " +
            "AND s.slotDate = :date " +
            "AND s.isAvailable = true " +
            "AND (s.slotDate > :today OR s.startTime > :nowTime) " +
            "ORDER BY s.startTime")
    List<DoctorAvailability> findBookableSlotsByDoctorAndDate(
            @Param("doctorId") String doctorId,
            @Param("date") LocalDate date,
            @Param("today") LocalDate today,
            @Param("nowTime") LocalTime nowTime);

    /** Unclaimed, not-yet-started slots for a doctor across all upcoming dates. */
    @EntityGraph(attributePaths = {"doctor"})
    @Query("SELECT s FROM DoctorAvailability s " +
            "WHERE s.doctor.doctorId = :doctorId " +
            "AND s.isAvailable = true " +
            "AND s.slotDate >= :today " +
            "AND (s.slotDate > :today OR s.startTime > :nowTime) " +
            "ORDER BY s.slotDate, s.startTime")
    List<DoctorAvailability> findBookableSlotsByDoctor(
            @Param("doctorId") String doctorId,
            @Param("today") LocalDate today,
            @Param("nowTime") LocalTime nowTime);

    /** Unclaimed, not-yet-started slots for a doctor within an inclusive date range. */
    @EntityGraph(attributePaths = {"doctor"})
    @Query("SELECT s FROM DoctorAvailability s " +
            "WHERE s.doctor.doctorId = :doctorId " +
            "AND s.slotDate BETWEEN :startDate AND :endDate " +
            "AND s.isAvailable = true " +
            "AND s.slotDate >= :today " +
            "AND (s.slotDate > :today OR s.startTime > :nowTime) " +
            "ORDER BY s.slotDate, s.startTime")
    List<DoctorAvailability> findBookableSlotsByDoctorAndDateRange(
            @Param("doctorId") String doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("today") LocalDate today,
            @Param("nowTime") LocalTime nowTime);

    /**
     * Slots for the same doctor/date whose interval intersects {@code [startTime, endTime)}.
     *
     * <p>Half-open on purpose: a slot ending exactly when the next begins does not overlap it.
     */
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
