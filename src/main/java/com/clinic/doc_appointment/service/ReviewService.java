package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorReplyRequest;
import com.clinic.doc_appointment.dto.request.ReviewRequest;
import com.clinic.doc_appointment.dto.response.ReviewResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Review;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.enums.ReviewSort;
import com.clinic.doc_appointment.exception.ForbiddenOperationException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            throw new ForbiddenOperationException("You can only review your own appointments");
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

        // 4. Update Doctor Stats (Denormalization) — recomputed from source rows, atomically
        doctorRepository.recomputeRatingStats(appointment.getDoctor().getDoctorId());

        return reviewMapper.toResponse(review);
    }

    @Transactional
    public ReviewResponse replyToReview(String doctorId, String reviewId, DoctorReplyRequest request) {
        Review review = EntityFinder.findOrThrow(reviewRepository, reviewId, "Review not found");

        if (!review.getDoctor().getDoctorId().equals(doctorId)) {
            throw new ForbiddenOperationException("You can only reply to reviews addressed to you");
        }

        review.setDoctorReply(request.getReply())
                .setRepliedAt(LocalDateTime.now());

        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getDoctorReviews(String doctorId, ReviewSort sortType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, sortType.sort());
        return reviewRepository.findByDoctorDoctorId(doctorId, pageable)
                .map(reviewMapper::toResponse);
    }

}
