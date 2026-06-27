package com.clinic.doc_appointment.mapper;

import com.clinic.doc_appointment.dto.response.AppointmentResponse;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.repository.ReviewRepository;
import com.clinic.doc_appointment.util.NameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppointmentMapper implements EntityMapper<Appointment, AppointmentResponse> {

    private final ReviewRepository reviewRepository;

    @Override
    public AppointmentResponse toResponse(Appointment appointment) {
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
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            reviewRepository.findByAppointmentAppointmentId(appointment.getAppointmentId())
                    .ifPresent(review -> response.setReviewId(review.getReviewId())
                            .setRating(review.getRating())
                            .setComment(review.getComment())
                            .setDoctorReply(review.getDoctorReply())
                            .setRepliedAt(review.getRepliedAt())
                            .setReviewCreatedAt(review.getCreatedAt()));
        }

        return response;
    }
}
