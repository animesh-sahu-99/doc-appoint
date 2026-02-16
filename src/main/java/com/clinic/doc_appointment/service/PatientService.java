package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.request.PatientUpdateRequest;
import com.clinic.doc_appointment.dto.response.PatientResponse;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.exception.DuplicateResourceException;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;

    @Transactional
    public PatientResponse registerPatient(PatientRegistrationRequest request) {
        log.info("Registering new patient with phone: {} {}",
                request.getCountryCode(), request.getPhoneNumber());

        // Check if phone number already exists
        if (patientRepository.existsByCountryCodeAndPhoneNumber(
                request.getCountryCode(), request.getPhoneNumber())) {
            throw new DuplicateResourceException("Phone number already registered");
        }

        // Check if email already exists (if provided)
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (patientRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already registered");
            }
        }

        // Create patient entity
        Patient patient = new Patient()
                .setFirstName(request.getName())
                .setEmail(request.getEmail())
                .setCountryCode(request.getCountryCode())
                .setPhoneNumber(request.getPhoneNumber())
                .setPassword(request.getPassword())  // TODO: Encrypt password
                .setGender(request.getGender())
                .setDateOfBirth(request.getDateOfBirth())
                .setAddress(request.getAddress());

        Patient savedPatient = patientRepository.save(patient);
        log.info("Patient registered successfully with ID: {}", savedPatient.getPatientId());

        return mapToResponse(savedPatient);
    }

    public PatientResponse getPatientById(String patientId) {
        Patient patient = findPatientById(patientId);
        return mapToResponse(patient);
    }

    public PatientResponse getPatientByPhone(String countryCode, String phoneNumber) {
        Patient patient = patientRepository.findByCountryCodeAndPhoneNumber(countryCode, phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with phone: " +
                        countryCode + " " + phoneNumber));
        return mapToResponse(patient);
    }

    public List<PatientResponse> getAllPatients() {
        return patientRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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

        return mapToResponse(updatedPatient);
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
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient not found with ID: " + patientId));
    }

    private PatientResponse mapToResponse(Patient patient) {
        String fullName = patient.getFirstName() +
                (patient.getLastName() != null ? " " + patient.getLastName() : "");

        return PatientResponse.builder()
                .patientId(patient.getPatientId())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .fullName(fullName)
                .email(patient.getEmail())
                .countryCode(patient.getCountryCode())
                .phoneNumber(patient.getPhoneNumber())
                .fullPhoneNumber(patient.getCountryCode() + " " + patient.getPhoneNumber())
                .gender(patient.getGender())
                .dateOfBirth(patient.getDateOfBirth())
                .address(patient.getAddress())
                .createdAt(patient.getCreatedAt())
                .build();
    }
}