package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.request.LoginRequest;
import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.AuthResponse;
import com.clinic.doc_appointment.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and registration endpoints for Doctors and Patients")
public class AuthController {

    private final AuthService authService;

    // ===================== DOCTOR =====================

    @PostMapping("/doctor/register")
    @Operation(summary = "Register a new Doctor", description = "Creates a doctor account and returns a JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> registerDoctor(
            @Valid @RequestBody DoctorRegistrationRequest request) {
        AuthResponse response = authService.registerDoctor(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Doctor registered successfully"));
    }

    @PostMapping("/doctor/login")
    @Operation(summary = "Doctor Login", description = "Authenticate a doctor with email and password, returns JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> loginDoctor(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.loginDoctor(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    // ===================== PATIENT =====================

    @PostMapping("/patient/register")
    @Operation(summary = "Register a new Patient", description = "Creates a patient account and returns a JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> registerPatient(
            @Valid @RequestBody PatientRegistrationRequest request) {
        AuthResponse response = authService.registerPatient(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Patient registered successfully"));
    }

    @PostMapping("/patient/login")
    @Operation(summary = "Patient Login", description = "Authenticate a patient with email and password, returns JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> loginPatient(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.loginPatient(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }
}
