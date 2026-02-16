package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.BulkSlotRequest;
import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.SlotResponse;
import com.clinic.doc_appointment.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @PostMapping
    public ResponseEntity<ApiResponse<SlotResponse>> createSlot(
            @Valid @RequestBody CreateSlotRequest request) {

        SlotResponse response = slotService.createSlot(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Slot created successfully"));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> createBulkSlots(
            @Valid @RequestBody BulkSlotRequest request) {

        List<SlotResponse> response = slotService.createBulkSlots(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, response.size() + " slots created successfully"));
    }

    @GetMapping("/{slotId}")
    public ResponseEntity<ApiResponse<SlotResponse>> getSlotById(@PathVariable String slotId) {

        SlotResponse response = slotService.getSlotById(slotId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlotsByDoctor(
            @PathVariable String doctorId) {

        List<SlotResponse> response = slotService.getAvailableSlotsByDoctor(doctorId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}/date/{date}")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlotsByDoctorAndDate(
            @PathVariable String doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<SlotResponse> response = slotService.getAvailableSlotsByDoctorAndDate(doctorId, date);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}/range")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlotsByDoctorAndDateRange(
            @PathVariable String doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<SlotResponse> response = slotService.getAvailableSlotsByDoctorAndDateRange(
                doctorId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}/date/{date}/all")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAllSlotsByDoctorAndDate(
            @PathVariable String doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<SlotResponse> response = slotService.getAllSlotsByDoctorAndDate(doctorId, date);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(@PathVariable String slotId) {

        slotService.deleteSlot(slotId);

        return ResponseEntity.ok(ApiResponse.success(null, "Slot deleted successfully"));
    }

    @DeleteMapping("/doctor/{doctorId}/date/{date}")
    public ResponseEntity<ApiResponse<Void>> deleteSlotsByDoctorAndDate(
            @PathVariable String doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        slotService.deleteSlotsByDoctorAndDate(doctorId, date);

        return ResponseEntity.ok(ApiResponse.success(null, "Slots deleted successfully"));
    }
}