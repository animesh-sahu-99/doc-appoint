package com.clinic.doc_appointment.mapper;

import com.clinic.doc_appointment.dto.response.PatientResponse;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.util.NameUtils;
import org.springframework.stereotype.Component;

@Component
public class PatientMapper implements EntityMapper<Patient, PatientResponse> {

    @Override
    public PatientResponse toResponse(Patient patient) {
        return PatientResponse.builder()
                .patientId(patient.getPatientId())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .fullName(NameUtils.fullName(patient.getFirstName(), patient.getLastName()))
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
