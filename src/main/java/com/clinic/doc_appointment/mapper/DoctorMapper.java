package com.clinic.doc_appointment.mapper;

import com.clinic.doc_appointment.dto.response.DoctorResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.util.NameUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DoctorMapper implements EntityMapper<Doctor, DoctorResponse> {

    @Override
    public DoctorResponse toResponse(Doctor doctor) {
        return DoctorResponse.builder()
                .doctorId(doctor.getDoctorId())
                .name(NameUtils.fullName(doctor.getFirstName(), doctor.getLastName()))
                .email(doctor.getEmail())
                .phone(doctor.getPhoneNumber())
                .specialization(doctor.getSpecialization())
                .specializationDisplayName(doctor.getSpecialization().getDisplayName())
                .specializationDescription(doctor.getSpecialization().getDescription())
                .qualification(doctor.getQualification())
                .experienceYears(doctor.getExperienceYears())
                .consultationFee(doctor.getConsultationFee())
                .profileImage(doctor.getProfileImage())
                .about(doctor.getAbout())
                .averageRating(displayRating(doctor.getAverageRating()))
                .totalReviews(doctor.getTotalReviews())
                .isActive(doctor.getIsActive())
                .build();
    }

    /**
     * Rounds to one decimal for display only.
     *
     * <p>Storage keeps the full average so nothing ever feeds a rounded number back into a
     * calculation — that was the source of the drift this denormalized column used to accumulate.
     */
    private static Double displayRating(Double averageRating) {
        if (averageRating == null) {
            return null;
        }
        return BigDecimal.valueOf(averageRating).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
