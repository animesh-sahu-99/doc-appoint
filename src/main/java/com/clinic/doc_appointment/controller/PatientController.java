package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.PatientUpdateRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.PatientResponse;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient profile endpoints.
 *
 * <p>Registration is <strong>not</strong> here — it belongs to {@code /api/auth/patient/register},
 * the only path that is public and the only one that issues tokens.
 *
 * <p>There is deliberately no "list all patients" or "look up patient by phone" endpoint. Both
 * existed, neither had a caller, and with no admin role to scope them to they were a full PII dump
 * and a phone-number enumeration oracle available to every signed-in user.
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "Patients", description = "Patient profile APIs")
public class PatientController {

    private final PatientService patientService;

    @GetMapping("/{patientId}")
    @Operation(summary = "Get a patient profile",
            description = "Readable by the patient themselves, or by a doctor who has an appointment with them")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientById(
            @PathVariable String patientId,
            @AuthenticationPrincipal UserPrincipal principal) {

        PatientResponse response = patientService.getPatientById(patientId, principal);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{patientId}")
    @Operation(summary = "Update own patient profile")
    public ResponseEntity<ApiResponse<PatientResponse>> updatePatient(
            @PathVariable String patientId,
            @Valid @RequestBody PatientUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        PatientResponse response = patientService.updatePatient(patientId, request, principal);

        return ResponseEntity.ok(ApiResponse.success(response, "Patient updated successfully"));
    }

    @DeleteMapping("/{patientId}")
    @Operation(summary = "Delete own patient account",
            description = "Only permitted while the account has no appointment, review or document history")
    public ResponseEntity<ApiResponse<Void>> deletePatient(
            @PathVariable String patientId,
            @AuthenticationPrincipal UserPrincipal principal) {

        patientService.deletePatient(patientId, principal);

        return ResponseEntity.ok(ApiResponse.success(null, "Patient deleted successfully"));
    }
}
