package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.request.LoginRequest;
import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.response.AuthResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import com.clinic.doc_appointment.security.JwtService;
import com.clinic.doc_appointment.security.LoginRateLimiter;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.registration.DoctorRegistrationService;
import com.clinic.doc_appointment.service.registration.PatientRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Authentication facade. Registration is delegated to role-specific
 * {@link com.clinic.doc_appointment.service.registration.AbstractRegistrationService} subclasses
 * (Template Method); login lives here because its flow genuinely differs from registration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final DoctorRegistrationService doctorRegistrationService;
    private final PatientRegistrationService patientRegistrationService;
    private final LoginRateLimiter loginRateLimiter;

    /**
     * Authenticate through the brute-force guard: reject blocked IPs up front, count a
     * failed attempt on bad credentials, and reset the IP's counter on success.
     */
    private Authentication authenticate(String email, String password, String clientIp) {
        loginRateLimiter.assertNotBlocked(clientIp);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            loginRateLimiter.recordSuccess(clientIp);
            return authentication;
        } catch (AuthenticationException ex) {
            loginRateLimiter.recordFailure(clientIp);
            throw ex;
        }
    }

    // ===================== DOCTOR AUTH =====================

    public AuthResponse registerDoctor(DoctorRegistrationRequest request) {
        log.info("Registering doctor with email: {}", request.getEmail());
        return doctorRegistrationService.register(request);
    }

    public AuthResponse loginDoctor(LoginRequest request, String clientIp) {
        log.info("Doctor login attempt: {}", request.getEmail());

        Authentication authentication = authenticate(request.getEmail(), request.getPassword(), clientIp);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        if (!Role.DOCTOR.authority().equals(principal.getRole())) {
            throw new BadCredentialsException("Invalid credentials for doctor login");
        }

        String token = jwtService.generateToken(principal);

        Doctor doctor = doctorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with email: " + request.getEmail()));

        return AuthResponse.builder()
                .token(token)
                .role(Role.DOCTOR.authority())
                .userId(principal.getId())
                .email(principal.getEmail())
                .name(doctor.getFirstName())
                .build();
    }

    // ===================== PATIENT AUTH =====================

    public AuthResponse registerPatient(PatientRegistrationRequest request) {
        log.info("Registering patient with email: {}", request.getEmail());
        return patientRegistrationService.register(request);
    }

    public AuthResponse loginPatient(LoginRequest request, String clientIp) {
        log.info("Patient login attempt: {}", request.getEmail());

        Authentication authentication = authenticate(request.getEmail(), request.getPassword(), clientIp);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        if (!Role.PATIENT.authority().equals(principal.getRole())) {
            throw new BadCredentialsException("Invalid credentials for patient login");
        }

        String token = jwtService.generateToken(principal);

        Patient patient = patientRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with email: " + request.getEmail()));

        return AuthResponse.builder()
                .token(token)
                .role(Role.PATIENT.authority())
                .userId(principal.getId())
                .email(principal.getEmail())
                .name(patient.getFirstName())
                .build();
    }
}
