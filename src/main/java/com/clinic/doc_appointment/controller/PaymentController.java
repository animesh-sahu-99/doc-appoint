package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.PaymentRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.PaymentResponse;
import com.clinic.doc_appointment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Payment initiated successfully"));
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentForAppointment(@PathVariable String appointmentId) {
        PaymentResponse response = paymentService.getPaymentForAppointment(appointmentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment fetched successfully"));
    }
}
