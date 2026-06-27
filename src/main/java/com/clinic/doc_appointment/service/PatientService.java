package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.request.PatientUpdateRequest;
import com.clinic.doc_appointment.dto.response.PatientResponse;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.exception.DuplicateResourceException;
import com.clinic.doc_appointment.mapper.PatientMapper;
import com.clinic.doc_appointment.repository.PatientRepository;
import com.clinic.doc_appointment.service.registration.RegistrationValidator;
import com.clinic.doc_appointment.util.EntityFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;  // ✅ Injected for BCrypt
    private final PatientMapper patientMapper;
    private final RegistrationValidator registrationValidator;

    @Transactional
    public PatientResponse registerPatient(PatientRegistrationRequest request) {
        log.info("Registering new patient with phone: {} {}",
                request.getCountryCode(), request.getPhoneNumber());

        // Cross-table uniqueness (shared with the /auth registration path)
        registrationValidator.validatePatientRegistration(
                request.getEmail(), request.getCountryCode(), request.getPhoneNumber());

        // Create patient entity
        Patient patient = new Patient()
                .setFirstName(request.getName())
                .setEmail(request.getEmail())
                .setCountryCode(request.getCountryCode())
                .setPhoneNumber(request.getPhoneNumber())
                .setPassword(passwordEncoder.encode(request.getPassword()))  // ✅ BCrypt hashed
                .setGender(request.getGender())
                .setDateOfBirth(request.getDateOfBirth())
                .setAddress(request.getAddress());

        Patient savedPatient = patientRepository.save(patient);
        log.info("Patient registered successfully with ID: {}", savedPatient.getPatientId());

        return patientMapper.toResponse(savedPatient);
    }

    public PatientResponse getPatientById(String patientId) {
        Patient patient = findPatientById(patientId);
        return patientMapper.toResponse(patient);
    }

    public PatientResponse getPatientByPhone(String countryCode, String phoneNumber) {
        Patient patient = EntityFinder.orThrow(
                patientRepository.findByCountryCodeAndPhoneNumber(countryCode, phoneNumber),
                "Patient not found with phone: " + countryCode + " " + phoneNumber);
        return patientMapper.toResponse(patient);
    }

    public List<PatientResponse> getAllPatients() {
        return patientMapper.toResponseList(patientRepository.findAll());
    }

    @Transactional
    public PatientResponse updatePatient(String patientId, PatientUpdateRequest request) {
        log.info("Updating patient: {}", patientId);

        Patient patient = findPatientById(patientId);

        // Check email uniqueness if being updated
        if (request.getEmail() != null && !request.getEmail().equals(patient.getEmail())) {
            if (patientRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already in use");
            }
            patient.setEmail(request.getEmail());
        }

        // Update other fields if provided
        if (request.getFirstName() != null) {
            patient.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            patient.setLastName(request.getLastName());
        }
        if (request.getGender() != null) {
            patient.setGender(request.getGender());
        }
        if (request.getDateOfBirth() != null) {
            patient.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getAddress() != null) {
            patient.setAddress(request.getAddress());
        }

        Patient updatedPatient = patientRepository.save(patient);
        log.info("Patient updated successfully: {}", patientId);

        return patientMapper.toResponse(updatedPatient);
    }

    @Transactional
    public void deletePatient(String patientId) {
        log.info("Deleting patient: {}", patientId);
        Patient patient = findPatientById(patientId);
        patientRepository.delete(patient);
        log.info("Patient deleted successfully: {}", patientId);
    }

    // =============== HELPER METHODS ===============

    private Patient findPatientById(String patientId) {
        return EntityFinder.findOrThrow(patientRepository, patientId, "Patient not found with ID: " + patientId);
    }
}
