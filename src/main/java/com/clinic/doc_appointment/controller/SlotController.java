package com.clinic.doc_appointment.controller;


import com.clinic.doc_appointment.dto.request.BulkSlotRequest;
import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.SlotResponse;
import com.clinic.doc_appointment.entity.DoctorAvailability;
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
    public ResponseEntity<ApiResponse<DoctorAvailability>> createSlot(
            @Valid @RequestBody CreateSlotRequest request) {
        DoctorAvailability slot = slotService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(slot, "Slot created successfully"));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<DoctorAvailability>>> createBulkSlots(
            @Valid @RequestBody BulkSlotRequest request) {
        List<DoctorAvailability> slots = slotService.createBulkSlots(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(slots, slots.size() + " slots created successfully"));
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlots(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<SlotResponse> slots = slotService.getAvailableSlots(doctorId, date);
        return ResponseEntity.ok(ApiResponse.success(slots, "Available slots retrieved"));
    }

    @GetMapping("/doctor/{doctorId}/range")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlotsInRange(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<SlotResponse> slots = slotService.getAvailableSlotsInRange(doctorId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(slots, "Available slots retrieved"));
    }
}
