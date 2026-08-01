package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {
    Page<Review> findByDoctorDoctorId(String doctorId, Pageable pageable);

    Optional<Review> findByAppointmentAppointmentId(String appointmentId);

    boolean existsByAppointmentAppointmentId(String appointmentId);

    long countByDoctorDoctorId(String doctorId);

    // AVG over the integer rating column is computed exactly in SQL — no compounding drift.
    // Returns null when the doctor has no reviews.
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.doctor.doctorId = :doctorId")
    Double findAverageRatingByDoctorId(@Param("doctorId") String doctorId);
}
