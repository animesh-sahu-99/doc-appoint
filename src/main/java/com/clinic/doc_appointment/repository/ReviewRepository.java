package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {
    /** {@code ReviewMapper} renders the patient's name and the appointment id. */
    @EntityGraph(attributePaths = {"appointment", "patient"})
    Page<Review> findByDoctorDoctorId(String doctorId, Pageable pageable);

    @EntityGraph(attributePaths = {"appointment", "patient"})
    Optional<Review> findByAppointmentAppointmentId(String appointmentId);

    /**
     * Batch form of {@link #findByAppointmentAppointmentId}. Mapping a list of appointments used to
     * issue one review query per row; this collapses that into a single round-trip.
     */
    @EntityGraph(attributePaths = {"appointment", "patient"})
    List<Review> findByAppointmentAppointmentIdIn(Collection<String> appointmentIds);

    boolean existsByAppointmentAppointmentId(String appointmentId);

    /** True if the patient has left any review — a patient with one cannot be hard-deleted. */
    boolean existsByPatientPatientId(String patientId);

    long countByDoctorDoctorId(String doctorId);

    // AVG over the integer rating column is computed exactly in SQL — no compounding drift.
    // Returns null when the doctor has no reviews.
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.doctor.doctorId = :doctorId")
    Double findAverageRatingByDoctorId(@Param("doctorId") String doctorId);
}
