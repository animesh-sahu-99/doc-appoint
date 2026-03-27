package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.DoctorFilterRequest;
import com.clinic.doc_appointment.dto.request.DoctorRegistrationRequest;
import com.clinic.doc_appointment.dto.request.DoctorUpdateRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.DoctorResponse;
import com.clinic.doc_appointment.enums.Specialization;
import com.clinic.doc_appointment.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<DoctorResponse>> registerDoctor(
            @Valid @RequestBody DoctorRegistrationRequest request) {
        DoctorResponse doctor = doctorService.registerDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(doctor, "Doctor registered successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> getDoctorById(@PathVariable String id) {
        DoctorResponse doctor = doctorService.getDoctorById(id);
        return ResponseEntity.ok(ApiResponse.success(doctor, "Doctor found"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> updateDoctor(
            @PathVariable String id,
            @Valid @RequestBody DoctorUpdateRequest request) {
        DoctorResponse doctor = doctorService.updateDoctor(id, request);
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
            @ModelAttribute DoctorFilterRequest filters) {
        List<DoctorResponse> doctors = doctorService.searchDoctors(filters);
        return ResponseEntity.ok(ApiResponse.success(doctors, "Doctors found"));
    }

    // ✅ Get by specialization enum
    @GetMapping("/specialization/{specialization}")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> getDoctorsBySpecialization(
            @PathVariable Specialization specialization) {
        List<DoctorResponse> doctors = doctorService.getDoctorsBySpecialization(specialization);
        return ResponseEntity.ok(ApiResponse.success(doctors, "Doctors found"));
    }

    // ✅ Get all available specializations (for dropdown)
    @GetMapping("/specializations")
    public ResponseEntity<ApiResponse<List<DoctorService.SpecializationInfo>>> getAllSpecializations() {
        List<DoctorService.SpecializationInfo> specializations = doctorService.getAllSpecializations();
        return ResponseEntity.ok(ApiResponse.success(specializations, "Specializations retrieved"));
    }

    // ✅ Get doctors with available slots by specialization
    @GetMapping("/available/specialization/{specialization}")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> getAvailableDoctors(
            @PathVariable Specialization specialization) {
        List<DoctorResponse> doctors = doctorService.getAvailableDoctorsBySpecialization(specialization);
        return ResponseEntity.ok(ApiResponse.success(doctors, "Available doctors found"));
    }
}
