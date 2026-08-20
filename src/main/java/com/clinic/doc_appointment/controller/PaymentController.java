package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.PaymentRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.PaymentResponse;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Appointment payment APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Initiate payment for your own appointment")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PaymentResponse response = paymentService.initiatePayment(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Payment initiated successfully"));
    }

    @GetMapping("/appointment/{appointmentId}")
    @Operation(summary = "Read the payment for an appointment you are party to")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentForAppointment(
            @PathVariable String appointmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        PaymentResponse response = paymentService.getPaymentForAppointment(appointmentId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment fetched successfully"));
    }
}
