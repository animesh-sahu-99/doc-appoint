package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.response.DoctorResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Specialization;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;

    @Transactional
    public DoctorResponse registerDoctor(DoctorRegistrationRequest request) {
        if (doctorRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        if (doctorRepository.existsByCountryCodeAndPhoneNumber(request.getCountryCode(), request.getPhoneNumber())) {
            throw new RuntimeException("Phone already registered");
        }

        Doctor doctor = new Doctor()
                .setFirstName(request.getName())
                .setEmail(request.getEmail())
                .setCountryCode(request.getCountryCode())
                .setPhoneNumber(request.getPhoneNumber())
                .setPassword(request.getPassword())  // Encrypt in production!
                .setSpecialization(request.getSpecialization())
                .setQualification(request.getQualification())
                .setExperienceYears(request.getExperienceYears())
                .setConsultationFee(request.getConsultationFee())
                .setAbout(request.getAbout())
                .setIsActive(true);

        Doctor savedDoctor = doctorRepository.save(doctor);
        return mapToResponse(savedDoctor);
    }

    public DoctorResponse getDoctorById(String doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + doctorId));
        return mapToResponse(doctor);
    }

    public List<DoctorResponse> getAllDoctors() {
        return doctorRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ✅ Get by specialization enum
    public List<DoctorResponse> getDoctorsBySpecialization(Specialization specialization) {
        return doctorRepository.findBySpecializationAndIsActiveTrue(specialization)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ✅ Get all specializations
    public List<SpecializationInfo> getAllSpecializations() {
        return Arrays.stream(Specialization.values())
                .map(spec -> new SpecializationInfo(
                        spec.name(),
                        spec.getDisplayName(),
                        spec.getDescription()
                ))
                .collect(Collectors.toList());
    }

    // ✅ Get available doctors by specialization
    public List<DoctorResponse> getAvailableDoctorsBySpecialization(Specialization specialization) {
        return doctorRepository.findAvailableDoctorsBySpecialization(specialization)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private DoctorResponse mapToResponse(Doctor doctor) {
        return new DoctorResponse()
                .setDoctorId(doctor.getDoctorId())
                .setName(doctor.getFirstName())
                .setEmail(doctor.getEmail())
                .setPhone(doctor.getPhoneNumber())
                .setSpecialization(doctor.getSpecialization())
                .setSpecializationDisplayName(doctor.getSpecialization().getDisplayName())
                .setSpecializationDescription(doctor.getSpecialization().getDescription())
                .setQualification(doctor.getQualification())
                .setExperienceYears(doctor.getExperienceYears())
                .setConsultationFee(doctor.getConsultationFee())
                .setProfileImage(doctor.getProfileImage())
                .setAbout(doctor.getAbout())
                .setIsActive(doctor.getIsActive());
    }

    // Inner class for specialization info
    public record SpecializationInfo(String code, String displayName, String description) {}
}
