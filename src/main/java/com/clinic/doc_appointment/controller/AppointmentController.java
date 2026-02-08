package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.BookAppointmentRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.AppointmentResponse;
import com.clinic.doc_appointment.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping("/book")
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request) {

        AppointmentResponse response = appointmentService.bookAppointment(request);

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
            @PathVariable String patientId) {

        List<AppointmentResponse> response = appointmentService.getPatientAppointments(patientId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getDoctorAppointments(
            @PathVariable String doctorId) {

        List<AppointmentResponse> response = appointmentService.getDoctorAppointments(doctorId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{appointmentId}/confirm")
    public ResponseEntity<ApiResponse<AppointmentResponse>> confirmAppointment(
            @PathVariable String appointmentId) {

        AppointmentResponse response = appointmentService.confirmAppointment(appointmentId);

        return ResponseEntity.ok(ApiResponse.success(response, "Appointment confirmed"));
    }

    @PutMapping("/{appointmentId}/cancel")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancelAppointment(
            @PathVariable String appointmentId) {

        AppointmentResponse response = appointmentService.cancelAppointment(appointmentId);

        return ResponseEntity.ok(ApiResponse.success(response, "Appointment cancelled"));
    }

    @PutMapping("/{appointmentId}/complete")
    public ResponseEntity<ApiResponse<AppointmentResponse>> completeAppointment(
            @PathVariable String appointmentId) {

        AppointmentResponse response = appointmentService.completeAppointment(appointmentId);

        return ResponseEntity.ok(ApiResponse.success(response, "Appointment completed"));
    }
}