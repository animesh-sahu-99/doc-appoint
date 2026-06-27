package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorReplyRequest;
import com.clinic.doc_appointment.dto.request.ReviewRequest;
import com.clinic.doc_appointment.dto.response.ReviewResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.Review;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.exception.InvalidStateException;
import com.clinic.doc_appointment.mapper.ReviewMapper;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.ReviewRepository;
import com.clinic.doc_appointment.util.EntityFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final ReviewMapper reviewMapper;

    @Transactional
    public ReviewResponse submitReview(String patientId, ReviewRequest request) {
        log.info("Submitting review for appointment: {}", request.getAppointmentId());

        Appointment appointment = EntityFinder.findOrThrow(appointmentRepository,
                request.getAppointmentId(), "Appointment not found");

        // 1. Validate ownership and status
        if (!appointment.getPatient().getPatientId().equals(patientId)) {
            throw new InvalidStateException("You can only review your own appointments");
        }

        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new InvalidStateException("You can only review completed appointments");
        }

        // 2. Check if already reviewed
        if (reviewRepository.existsByAppointmentAppointmentId(appointment.getAppointmentId())) {
            throw new InvalidStateException("This appointment has already been reviewed");
        }

        // 3. Create Review
        Review review = new Review()
                .setAppointment(appointment)
                .setDoctor(appointment.getDoctor())
                .setPatient(appointment.getPatient())
                .setRating(request.getRating())
                .setComment(request.getComment());

        review = reviewRepository.save(review);

        // 4. Update Doctor Stats (Denormalization)
        updateDoctorStats(appointment.getDoctor(), request.getRating());

        return reviewMapper.toResponse(review);
    }

    @Transactional
    public ReviewResponse replyToReview(String doctorId, String reviewId, DoctorReplyRequest request) {
        Review review = EntityFinder.findOrThrow(reviewRepository, reviewId, "Review not found");

        if (!review.getDoctor().getDoctorId().equals(doctorId)) {
            throw new InvalidStateException("You can only reply to reviews addressed to you");
        }

        review.setDoctorReply(request.getReply())
                .setRepliedAt(LocalDateTime.now());

        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    public Page<ReviewResponse> getDoctorReviews(String doctorId, String sortType, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        if ("high".equalsIgnoreCase(sortType)) {
            sort = Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        } else if ("low".equalsIgnoreCase(sortType)) {
            sort = Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        return reviewRepository.findByDoctorDoctorId(doctorId, pageable)
                .map(reviewMapper::toResponse);
    }

    private void updateDoctorStats(Doctor doctor, Integer newRating) {
        // Defensive null-handling for legacy data
        int currentTotalReviews = (doctor.getTotalReviews() != null) ? doctor.getTotalReviews() : 0;
        double currentAverageRating = (doctor.getAverageRating() != null) ? doctor.getAverageRating() : 0.0;

        int newTotalReviews = currentTotalReviews + 1;
        double currentTotalSum = currentAverageRating * currentTotalReviews;
        double newAverage = (currentTotalSum + newRating) / newTotalReviews;

        // Round to 1 decimal place
        BigDecimal bd = new BigDecimal(Double.toString(newAverage));
        bd = bd.setScale(1, RoundingMode.HALF_UP);

        doctor.setTotalReviews(newTotalReviews);
        doctor.setAverageRating(bd.doubleValue());

        doctorRepository.save(doctor);
        log.info("Updated stats for doctor {}: new avg {}, total {}",
                doctor.getDoctorId(), doctor.getAverageRating(), doctor.getTotalReviews());
    }
}
