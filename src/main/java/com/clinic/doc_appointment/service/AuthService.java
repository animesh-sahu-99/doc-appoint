package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.request.LoginRequest;
import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.response.AuthResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.exception.DuplicateResourceException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import com.clinic.doc_appointment.security.JwtService;
import com.clinic.doc_appointment.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // ===================== DOCTOR AUTH =====================

    @Transactional
    public AuthResponse registerDoctor(DoctorRegistrationRequest request) {
        log.info("Registering doctor with email: {}", request.getEmail());

        if (doctorRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }
        // ✅ Cross-table check: prevent same email in both doctor and patient tables
        if (patientRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }
        if (doctorRepository.existsByCountryCodeAndPhoneNumber(request.getCountryCode(), request.getPhoneNumber())) {
            throw new DuplicateResourceException("Phone number already registered");
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

        Doctor saved = doctorRepository.save(doctor);
        log.info("Doctor registered: {}", saved.getDoctorId());

        UserPrincipal principal = new UserPrincipal(saved.getDoctorId(), saved.getEmail(), saved.getPassword(), "ROLE_DOCTOR");
        String token = jwtService.generateToken(principal);

        return AuthResponse.builder()
                .token(token)
                .role("ROLE_DOCTOR")
                .userId(saved.getDoctorId())
                .email(saved.getEmail())
                .name(saved.getFirstName())
                .build();
    }

    public AuthResponse loginDoctor(LoginRequest request) {
        log.info("Doctor login attempt: {}", request.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        if (!"ROLE_DOCTOR".equals(principal.getRole())) {
            throw new BadCredentialsException("Invalid credentials for doctor login");
        }

        String token = jwtService.generateToken(principal);

        Doctor doctor = doctorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with email: " + request.getEmail()));

        return AuthResponse.builder()
                .token(token)
                .role("ROLE_DOCTOR")
                .userId(principal.getId())
                .email(principal.getEmail())
                .name(doctor.getFirstName())
                .build();
    }

    // ===================== PATIENT AUTH =====================

    @Transactional
    public AuthResponse registerPatient(PatientRegistrationRequest request) {
        log.info("Registering patient with email: {}", request.getEmail());

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (patientRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already registered");
            }
            // ✅ Cross-table check: prevent same email in both doctor and patient tables
            if (doctorRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already registered");
            }
        }
        if (patientRepository.existsByCountryCodeAndPhoneNumber(request.getCountryCode(), request.getPhoneNumber())) {
            throw new DuplicateResourceException("Phone number already registered");
        }

        Patient patient = new Patient()
                .setFirstName(request.getName())
                .setEmail(request.getEmail())
                .setCountryCode(request.getCountryCode())
                .setPhoneNumber(request.getPhoneNumber())
                .setPassword(passwordEncoder.encode(request.getPassword()))  // ✅ BCrypt hashed
                .setGender(request.getGender())
                .setDateOfBirth(request.getDateOfBirth())
                .setAddress(request.getAddress());

        Patient saved = patientRepository.save(patient);
        log.info("Patient registered: {}", saved.getPatientId());

        UserPrincipal principal = new UserPrincipal(saved.getPatientId(), saved.getEmail(), saved.getPassword(), "ROLE_PATIENT");
        String token = jwtService.generateToken(principal);

        return AuthResponse.builder()
                .token(token)
                .role("ROLE_PATIENT")
                .userId(saved.getPatientId())
                .email(saved.getEmail())
                .name(saved.getFirstName())
                .build();
    }

    public AuthResponse loginPatient(LoginRequest request) {
        log.info("Patient login attempt: {}", request.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        if (!"ROLE_PATIENT".equals(principal.getRole())) {
            throw new BadCredentialsException("Invalid credentials for patient login");
        }

        String token = jwtService.generateToken(principal);

        Patient patient = patientRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with email: " + request.getEmail()));

        return AuthResponse.builder()
                .token(token)
                .role("ROLE_PATIENT")
                .userId(principal.getId())
                .email(principal.getEmail())
                .name(patient.getFirstName())
                .build();
    }
}
