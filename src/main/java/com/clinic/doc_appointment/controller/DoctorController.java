package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.DoctorFilterRequest;
import com.clinic.doc_appointment.dto.request.DoctorUpdateRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.DoctorResponse;
import com.clinic.doc_appointment.enums.Specialization;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctors", description = "Doctor discovery and profile APIs")
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> getDoctorById(@PathVariable String id) {
        DoctorResponse doctor = doctorService.getDoctorById(id);
        return ResponseEntity.ok(ApiResponse.success(doctor, "Doctor found"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Update own doctor profile",
            description = "A doctor may only update their own profile; the id must match the caller")
    public ResponseEntity<ApiResponse<DoctorResponse>> updateDoctor(
            @PathVariable String id,
            @Valid @RequestBody DoctorUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        DoctorResponse doctor = doctorService.updateDoctor(id, request, principal);
        return ResponseEntity.ok(ApiResponse.success(doctor, "Doctor profile updated successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> getAllDoctors() {
        List<DoctorResponse> doctors = doctorService.getAllDoctors();
        return ResponseEntity.ok(ApiResponse.success(doctors, "Doctors retrieved"));
    }

    /**
     * Unified search endpoint with optional filters.
     * All query params are optional. When omitted, behaves like GET /api/doctors.
     *
     * GET /api/doctors/search?name=john&specialization=CARDIOLOGIST&minFee=0&maxFee=500&minExperience=5&availableOnly=true
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> searchDoctors(
            @Valid @ModelAttribute DoctorFilterRequest filters) {
        List<DoctorResponse> doctors = doctorService.searchDoctors(filters);
        return ResponseEntity.ok(ApiResponse.success(doctors, "Doctors found"));
    }

    @GetMapping("/specialization/{specialization}")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> getDoctorsBySpecialization(
            @PathVariable Specialization specialization) {
        List<DoctorResponse> doctors = doctorService.getDoctorsBySpecialization(specialization);
        return ResponseEntity.ok(ApiResponse.success(doctors, "Doctors found"));
    }

    @GetMapping("/specializations")
    public ResponseEntity<ApiResponse<List<DoctorService.SpecializationInfo>>> getAllSpecializations() {
        List<DoctorService.SpecializationInfo> specializations = doctorService.getAllSpecializations();
        return ResponseEntity.ok(ApiResponse.success(specializations, "Specializations retrieved"));
    }

    @GetMapping("/available/specialization/{specialization}")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> getAvailableDoctors(
            @PathVariable Specialization specialization) {
        List<DoctorResponse> doctors = doctorService.getAvailableDoctorsBySpecialization(specialization);
        return ResponseEntity.ok(ApiResponse.success(doctors, "Available doctors found"));
    }
}
