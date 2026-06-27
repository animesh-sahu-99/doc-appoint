package com.clinic.doc_appointment.mapper;

import com.clinic.doc_appointment.dto.response.ReviewResponse;
import com.clinic.doc_appointment.entity.Review;
import com.clinic.doc_appointment.util.NameUtils;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper implements EntityMapper<Review, ReviewResponse> {

    @Override
    public ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
                .reviewId(review.getReviewId())
                .appointmentId(review.getAppointment().getAppointmentId())
                .patientName(NameUtils.fullName(review.getPatient().getFirstName(), review.getPatient().getLastName()))
                .rating(review.getRating())
                .comment(review.getComment())
                .doctorReply(review.getDoctorReply())
                .repliedAt(review.getRepliedAt())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
