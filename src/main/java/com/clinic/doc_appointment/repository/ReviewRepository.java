package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {
    Page<Review> findByDoctorDoctorId(String doctorId, Pageable pageable);
    
    Optional<Review> findByAppointmentAppointmentId(String appointmentId);
    
    boolean existsByAppointmentAppointmentId(String appointmentId);
}
