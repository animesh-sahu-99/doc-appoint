package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.BookAppointmentRequest;
import com.clinic.doc_appointment.dto.request.UpdateAppointmentNotesRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.AppointmentResponse;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments", description = "Appointment management APIs - booking, cancellation, and status updates")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping("/book")
    @Operation(summary = "Book an appointment", description = "Book a new appointment for a patient with a specific doctor slot")
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        AppointmentResponse response = appointmentService.bookAppointment(request, principal);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Appointment booked successfully"));
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(
            @PathVariable String appointmentId) {

        AppointmentResponse response = appointmentService.getAppointmentById(appointmentId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/number/{appointmentNumber}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentByNumber(
            @PathVariable String appointmentNumber) {

        AppointmentResponse response = appointmentService.getAppointmentByNumber(appointmentNumber);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getPatientAppointments(
            @PathVariable String patientId,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<AppointmentResponse> response = appointmentService.getPatientAppointments(patientId, principal);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/patient/{patientId}/upcoming")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getUpcomingPatientAppointments(
            @PathVariable String patientId,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<AppointmentResponse> response = appointmentService.getUpcomingPatientAppointments(patientId, principal);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getDoctorAppointments(
            @PathVariable String doctorId,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<AppointmentResponse> response = appointmentService.getDoctorAppointments(doctorId, principal);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}/upcoming")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getUpcomingDoctorAppointments(
            @PathVariable String doctorId,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<AppointmentResponse> response = appointmentService.getUpcomingDoctorAppointments(doctorId, principal);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}/date/{date}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getDoctorAppointmentsByDate(
            @PathVariable String doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<AppointmentResponse> response = appointmentService.getDoctorAppointmentsByDate(doctorId, date, principal);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{appointmentId}/confirm")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> confirmAppointment(
            @PathVariable String appointmentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        AppointmentResponse response = appointmentService.confirmAppointment(appointmentId, principal);

        return ResponseEntity.ok(ApiResponse.success(response, "Appointment confirmed"));
    }

    @PutMapping("/{appointmentId}/cancel")
    @Operation(summary = "Cancel appointment", description = "Cancel an appointment and automatically free the slot")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancelAppointment(
            @PathVariable String appointmentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        AppointmentResponse response = appointmentService.cancelAppointment(appointmentId, principal);

        return ResponseEntity.ok(ApiResponse.success(response, "Appointment cancelled"));
    }

    @PutMapping("/{appointmentId}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> completeAppointment(
            @PathVariable String appointmentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        AppointmentResponse response = appointmentService.completeAppointment(appointmentId, principal);

        return ResponseEntity.ok(ApiResponse.success(response, "Appointment completed"));
    }

    @PutMapping("/{appointmentId}/no-show")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> markNoShow(
            @PathVariable String appointmentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        AppointmentResponse response = appointmentService.markNoShow(appointmentId, principal);

        return ResponseEntity.ok(ApiResponse.success(response, "Appointment marked as no-show"));
    }

    @PutMapping("/{appointmentId}/notes")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Update Appointment Notes", description = "Allows doctors to add or update clinical notes/prescriptions.")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateNotes(
            @PathVariable String appointmentId,
            @Valid @RequestBody UpdateAppointmentNotesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        AppointmentResponse response = appointmentService.updateAppointmentNotes(appointmentId, request.getNotes(), principal);

        return ResponseEntity.ok(ApiResponse.success(response, "Notes updated successfully"));
    }
}