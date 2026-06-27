package com.clinic.doc_appointment.service.registration;

import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.response.AuthResponse;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.repository.PatientRepository;
import com.clinic.doc_appointment.security.JwtService;
import com.clinic.doc_appointment.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Patient-specific registration steps for {@link AbstractRegistrationService}. Duplicate checks are
 * delegated to the shared {@link RegistrationValidator}; patient email stays optional but, when
 * present, must be unique across both tables.
 */
@Service
public class PatientRegistrationService extends AbstractRegistrationService<PatientRegistrationRequest, Patient> {

    private final PatientRepository patientRepository;
    private final RegistrationValidator registrationValidator;

    public PatientRegistrationService(PasswordEncoder passwordEncoder,
                                      JwtService jwtService,
                                      PatientRepository patientRepository,
                                      RegistrationValidator registrationValidator) {
        super(passwordEncoder, jwtService);
        this.patientRepository = patientRepository;
        this.registrationValidator = registrationValidator;
    }

    @Override
    protected void validateDuplicates(PatientRegistrationRequest request) {
        registrationValidator.validatePatientRegistration(
                request.getEmail(), request.getCountryCode(), request.getPhoneNumber());
    }

    @Override
    protected Patient buildEntity(PatientRegistrationRequest request) {
        return new Patient()
                .setFirstName(request.getName())
                .setEmail(request.getEmail())
                .setCountryCode(request.getCountryCode())
                .setPhoneNumber(request.getPhoneNumber())
                .setPassword(passwordEncoder.encode(request.getPassword()))
                .setGender(request.getGender())
                .setDateOfBirth(request.getDateOfBirth())
                .setAddress(request.getAddress());
    }

    @Override
    protected Patient persist(Patient entity) {
        return patientRepository.save(entity);
    }

    @Override
    protected UserPrincipal toPrincipal(Patient saved) {
        return new UserPrincipal(saved.getPatientId(), saved.getEmail(), saved.getPassword(), Role.PATIENT.authority());
    }

    @Override
    protected AuthResponse toAuthResponse(Patient saved, String token) {
        return AuthResponse.builder()
                .token(token)
                .role(Role.PATIENT.authority())
                .userId(saved.getPatientId())
                .email(saved.getEmail())
                .name(saved.getFirstName())
                .build();
    }
}
