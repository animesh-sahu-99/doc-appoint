package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.request.PatientUpdateRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.PatientResponse;
import com.clinic.doc_appointment.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<PatientResponse>> registerPatient(
            @Valid @RequestBody PatientRegistrationRequest request) {

        PatientResponse response = patientService.registerPatient(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Patient registered successfully"));
    }

    @GetMapping("/{patientId}")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientById(
            @PathVariable String patientId) {

        PatientResponse response = patientService.getPatientById(patientId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/phone")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientByPhone(
            @RequestParam String countryCode,
            @RequestParam String phoneNumber) {

        PatientResponse response = patientService.getPatientByPhone(countryCode, phoneNumber);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PatientResponse>>> getAllPatients() {

        List<PatientResponse> response = patientService.getAllPatients();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{patientId}")
    public ResponseEntity<ApiResponse<PatientResponse>> updatePatient(
            @PathVariable String patientId,
            @Valid @RequestBody PatientUpdateRequest request) {

        PatientResponse response = patientService.updatePatient(patientId, request);

        return ResponseEntity.ok(ApiResponse.success(response, "Patient updated successfully"));
    }

    @DeleteMapping("/{patientId}")
    public ResponseEntity<ApiResponse<Void>> deletePatient(@PathVariable String patientId) {

        patientService.deletePatient(patientId);

        return ResponseEntity.ok(ApiResponse.success(null, "Patient deleted successfully"));
    }
}