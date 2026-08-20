package com.clinic.doc_appointment.mapper;

import com.clinic.doc_appointment.dto.response.AppointmentResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.entity.Review;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.repository.ReviewRepository;
import com.clinic.doc_appointment.util.NameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AppointmentMapper implements EntityMapper<Appointment, AppointmentResponse> {

    private final ReviewRepository reviewRepository;

    @Override
    public AppointmentResponse toResponse(Appointment appointment) {
        // Single-entity path: look the review up directly.
        Review review = appointment.getStatus() == AppointmentStatus.COMPLETED
                ? reviewRepository.findByAppointmentAppointmentId(appointment.getAppointmentId()).orElse(null)
                : null;
        return toResponse(appointment, review);
    }

    /**
     * List path with the review lookup batched into one query.
     *
     * <p>The inherited default calls {@link #toResponse(Appointment)} per element, and that method
     * queries for a review — so rendering a patient's 40-appointment history cost 41 queries. Here
     * the completed appointments are collected first and their reviews fetched in a single
     * {@code IN} query.
     */
    @Override
    public List<AppointmentResponse> toResponseList(Collection<Appointment> appointments) {
        List<String> completedIds = appointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.COMPLETED)
                .map(Appointment::getAppointmentId)
                .toList();

        Map<String, Review> reviewsByAppointmentId = completedIds.isEmpty()
                ? Map.of()
                : reviewRepository.findByAppointmentAppointmentIdIn(completedIds).stream()
                        .collect(Collectors.toMap(
                                review -> review.getAppointment().getAppointmentId(),
                                Function.identity(),
                                // A review is one-to-one with an appointment; keep the first if that
                                // ever stops holding rather than throwing in a read path.
                                (first, duplicate) -> first));

        return appointments.stream()
                .map(appointment -> toResponse(
                        appointment, reviewsByAppointmentId.get(appointment.getAppointmentId())))
                .toList();
    }

    private AppointmentResponse toResponse(Appointment appointment, Review review) {
        Doctor doctor = appointment.getDoctor();
        Patient patient = appointment.getPatient();
        DoctorAvailability slot = appointment.getSlot();

        AppointmentResponse response = AppointmentResponse.builder()
                .appointmentId(appointment.getAppointmentId())
                .appointmentNumber(appointment.getAppointmentNumber())
                .patientId(patient.getPatientId())
                .patientName(NameUtils.fullName(patient.getFirstName(), patient.getLastName()))
                .patientPhone(patient.getCountryCode() + " " + patient.getPhoneNumber())
                .doctorId(doctor.getDoctorId())
                .doctorName(NameUtils.fullName(doctor.getFirstName(), doctor.getLastName()))
                .specialization(String.valueOf(doctor.getSpecialization()))
                .consultationFee(doctor.getConsultationFee())
                .slotId(slot.getSlotId())
                .appointmentDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .durationMinutes(slot.getDurationMinutes())
                .status(appointment.getStatus())
                .reasonForVisit(appointment.getReasonForVisit())
                .notes(appointment.getNotes())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();

        // Review enrichment is only attached for COMPLETED appointments (unchanged behavior).
        if (appointment.getStatus() == AppointmentStatus.COMPLETED && review != null) {
            response.setReviewId(review.getReviewId())
                    .setRating(review.getRating())
                    .setComment(review.getComment())
                    .setDoctorReply(review.getDoctorReply())
                    .setRepliedAt(review.getRepliedAt())
                    .setReviewCreatedAt(review.getCreatedAt());
        }

        return response;
    }
}
