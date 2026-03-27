package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorFilterRequest;
import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.request.DoctorUpdateRequest;
import com.clinic.doc_appointment.dto.response.DoctorResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Specialization;
import com.clinic.doc_appointment.exception.DuplicateResourceException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.specification.DoctorSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;  // ✅ Injected for BCrypt

    @Transactional
    public DoctorResponse registerDoctor(DoctorRegistrationRequest request) {
        if (doctorRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }

        if (doctorRepository.existsByCountryCodeAndPhoneNumber(request.getCountryCode(), request.getPhoneNumber())) {
            throw new DuplicateResourceException("Phone already registered");
        }

        Doctor doctor = new Doctor()
                .setFirstName(request.getName())
                .setEmail(request.getEmail())
                .setCountryCode(request.getCountryCode())
                .setPhoneNumber(request.getPhoneNumber())
                .setPassword(passwordEncoder.encode(request.getPassword()))  // ✅ BCrypt hashed
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

    @Transactional
    public DoctorResponse updateDoctor(String doctorId, DoctorUpdateRequest request) {
        // Validate doctor exists first
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + doctorId));

        // Use direct JPQL update to avoid CascadeType.ALL cascade issues on appointments/slots
        String name = (request.getName() != null && !request.getName().isBlank()) ? request.getName() : null;
        String qualification = (request.getQualification() != null && !request.getQualification().isBlank()) ? request.getQualification() : null;

        doctorRepository.updateProfileFields(
                doctorId,
                name,
                qualification,
                request.getExperienceYears(),
                request.getConsultationFee(),
                request.getAbout()
        );

        // Re-fetch updated doctor and return as response
        Doctor updated = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found after update"));
        return mapToResponse(updated);
    }

    public List<DoctorResponse> getAllDoctors() {
        return doctorRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Dynamic multi-filter search. All filter params are optional.
     * Falls back to "all active doctors" when no filters are set.
     */
    public List<DoctorResponse> searchDoctors(DoctorFilterRequest filters) {
        Specification<Doctor> spec = DoctorSpecification.withFilters(filters);
        return doctorRepository.findAll(spec)
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
