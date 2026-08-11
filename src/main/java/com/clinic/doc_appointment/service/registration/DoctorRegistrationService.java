package com.clinic.doc_appointment.service.registration;

import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.response.AuthResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.security.IssuedTokens;
import com.clinic.doc_appointment.security.TokenIssuer;
import com.clinic.doc_appointment.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Doctor-specific registration steps for {@link AbstractRegistrationService}. Duplicate checks are
 * delegated to the shared {@link RegistrationValidator} so every registration path enforces the same
 * cross-table email rule.
 */
@Service
public class DoctorRegistrationService extends AbstractRegistrationService<DoctorRegistrationRequest, Doctor> {

    private final DoctorRepository doctorRepository;
    private final RegistrationValidator registrationValidator;

    public DoctorRegistrationService(PasswordEncoder passwordEncoder,
                                     TokenIssuer tokenIssuer,
                                     DoctorRepository doctorRepository,
                                     RegistrationValidator registrationValidator) {
        super(passwordEncoder, tokenIssuer);
        this.doctorRepository = doctorRepository;
        this.registrationValidator = registrationValidator;
    }

    @Override
    protected void validateDuplicates(DoctorRegistrationRequest request) {
        registrationValidator.validateDoctorRegistration(
                request.getEmail(), request.getCountryCode(), request.getPhoneNumber());
    }

    @Override
    protected Doctor buildEntity(DoctorRegistrationRequest request) {
        return new Doctor()
                .setFirstName(request.getName())
                .setEmail(request.getEmail())
                .setCountryCode(request.getCountryCode())
                .setPhoneNumber(request.getPhoneNumber())
                .setPassword(passwordEncoder.encode(request.getPassword()))
                .setSpecialization(request.getSpecialization())
                .setQualification(request.getQualification())
                .setExperienceYears(request.getExperienceYears())
                .setConsultationFee(request.getConsultationFee())
                .setAbout(request.getAbout())
                .setIsActive(true);
    }

    @Override
    protected Doctor persist(Doctor entity) {
        return doctorRepository.save(entity);
    }

    @Override
    protected UserPrincipal toPrincipal(Doctor saved) {
        return new UserPrincipal(saved.getDoctorId(), saved.getEmail(), saved.getPassword(), Role.DOCTOR.authority());
    }

    @Override
    protected AuthResponse toAuthResponse(Doctor saved, IssuedTokens tokens) {
        return tokens.decorate(AuthResponse.builder())
                .role(Role.DOCTOR.authority())
                .userId(saved.getDoctorId())
                .email(saved.getEmail())
                .name(saved.getFirstName())
                .build();
    }
}
