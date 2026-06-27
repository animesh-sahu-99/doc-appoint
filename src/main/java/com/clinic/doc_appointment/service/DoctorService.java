package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorFilterRequest;
import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.request.DoctorUpdateRequest;
import com.clinic.doc_appointment.dto.response.DoctorResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Specialization;
import com.clinic.doc_appointment.mapper.DoctorMapper;
import com.clinic.doc_appointment.service.registration.RegistrationValidator;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.specification.DoctorSpecification;
import com.clinic.doc_appointment.util.EntityFinder;
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
    private final DoctorMapper doctorMapper;
    private final RegistrationValidator registrationValidator;

    @Transactional
    public DoctorResponse registerDoctor(DoctorRegistrationRequest request) {
        registrationValidator.validateDoctorRegistration(
                request.getEmail(), request.getCountryCode(), request.getPhoneNumber());

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
        return doctorMapper.toResponse(savedDoctor);
    }

    public DoctorResponse getDoctorById(String doctorId) {
        Doctor doctor = EntityFinder.findOrThrow(doctorRepository, doctorId, "Doctor not found with id: " + doctorId);
        return doctorMapper.toResponse(doctor);
    }

    @Transactional
    public DoctorResponse updateDoctor(String doctorId, DoctorUpdateRequest request) {
        // Validate doctor exists first
        EntityFinder.findOrThrow(doctorRepository, doctorId, "Doctor not found with id: " + doctorId);

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
        Doctor updated = EntityFinder.findOrThrow(doctorRepository, doctorId, "Doctor not found after update");
        return doctorMapper.toResponse(updated);
    }

    public List<DoctorResponse> getAllDoctors() {
        return doctorMapper.toResponseList(doctorRepository.findByIsActiveTrue());
    }

    /**
     * Dynamic multi-filter search. All filter params are optional.
     * Falls back to "all active doctors" when no filters are set.
     */
    public List<DoctorResponse> searchDoctors(DoctorFilterRequest filters) {
        Specification<Doctor> spec = DoctorSpecification.withFilters(filters);
        return doctorMapper.toResponseList(doctorRepository.findAll(spec));
    }

    // ✅ Get by specialization enum
    public List<DoctorResponse> getDoctorsBySpecialization(Specialization specialization) {
        return doctorMapper.toResponseList(doctorRepository.findBySpecializationAndIsActiveTrue(specialization));
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
        return doctorMapper.toResponseList(doctorRepository.findAvailableDoctorsBySpecialization(specialization));
    }

    // Inner class for specialization info
    public record SpecializationInfo(String code, String displayName, String description) {}
}
