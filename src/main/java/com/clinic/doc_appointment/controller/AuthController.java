package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.request.LoginRequest;
import com.clinic.doc_appointment.dto.request.LogoutRequest;
import com.clinic.doc_appointment.dto.request.PatientRegistrationRequest;
import com.clinic.doc_appointment.dto.request.RefreshTokenRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.AuthResponse;
import com.clinic.doc_appointment.security.TokenContext;
import com.clinic.doc_appointment.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, registration and session endpoints for Doctors and Patients")
public class AuthController {

    private final AuthService authService;

    @Value("${ratelimit.login.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    private TokenContext contextOf(HttpServletRequest httpRequest) {
        return TokenContext.from(httpRequest, trustForwardedFor);
    }

    // ===================== DOCTOR =====================

    @PostMapping("/doctor/register")
    @Operation(summary = "Register a new Doctor", description = "Creates a doctor account and returns an access + refresh token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> registerDoctor(
            @Valid @RequestBody DoctorRegistrationRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.registerDoctor(request, contextOf(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response, "Doctor registered successfully"));
    }

    @PostMapping("/doctor/login")
    @Operation(summary = "Doctor Login", description = "Authenticate a doctor with email and password, returns an access + refresh token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> loginDoctor(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.loginDoctor(request, contextOf(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    // ===================== PATIENT =====================

    @PostMapping("/patient/register")
    @Operation(summary = "Register a new Patient", description = "Creates a patient account and returns an access + refresh token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> registerPatient(
            @Valid @RequestBody PatientRegistrationRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.registerPatient(request, contextOf(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response, "Patient registered successfully"));
    }

    @PostMapping("/patient/login")
    @Operation(summary = "Patient Login", description = "Authenticate a patient with email and password, returns an access + refresh token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> loginPatient(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.loginPatient(request, contextOf(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    // ===================== SESSION =====================

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token",
            description = "Exchanges a valid refresh token for a new access token and a rotated refresh token. "
                    + "The submitted token is consumed and must not be reused. "
                    + "Returns 401 for any unusable token — expired, revoked, unknown or replayed.")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(request.getRefreshToken(), contextOf(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    /**
     * Always succeeds, including for an unknown or already-revoked token: a client retrying over
     * a flaky connection must not see an error, and the response must not reveal whether a token
     * exists.
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout",
            description = "Revokes the refresh token's device session, or every session for the user when "
                    + "allDevices is true. Idempotent — always returns 200.")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        if (request.isAllDevices()) {
            authService.logoutAllDevices(request.getRefreshToken());
        } else {
            authService.logout(request.getRefreshToken());
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out"));
    }
}
