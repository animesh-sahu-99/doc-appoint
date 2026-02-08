package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    @Transactional
    public Patient registerPatient(PatientRegistrationRequest request) {
        // Check if email already exists
        if (patientRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Check if phone already exists
        if (patientRepository.existsByPhone(request.getPhoneNumber())) {
            throw new RuntimeException("Phone number already registered");
        }

        Patient patient = new Patient()
                .setFirstName(request.getName())
                .setEmail(request.getEmail())
                .setPhoneNumber(request.getPhoneNumber())
                .setPassword(request.getPassword())  // In production, encrypt this!
                .setGender(request.getGender())
                .setDateOfBirth(request.getDateOfBirth())
                .setAddress(request.getAddress());

        return patientRepository.save(patient);
    }

    public Patient getPatientById(String patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with id: " + patientId));
    }

    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    public Patient getPatientByEmail(String email) {
        return patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with email: " + email));
    }
}
