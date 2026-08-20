package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.BulkSlotRequest;
import com.clinic.doc_appointment.dto.request.CreateSlotRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.SlotResponse;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.SlotService;
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

/**
 * Doctor availability slot management.
 *
 * <p>Every mutation is doctor-only <em>and</em> owner-scoped. {@code @PreAuthorize} handles the
 * coarse role gate here; {@code SlotService} compares the target doctor against the caller, because
 * the {@code doctorId} is supplied by the client and a role check alone would still let one doctor
 * edit another's calendar.
 *
 * <p>Reads are intentionally open to any authenticated user — patients browse availability to book.
 */
@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
@Tag(name = "Slots", description = "Doctor availability slot management APIs")
public class SlotController {

    private final SlotService slotService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Create a single slot", description = "Create a new availability slot on your own calendar")
    public ResponseEntity<ApiResponse<SlotResponse>> createSlot(
            @Valid @RequestBody CreateSlotRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        SlotResponse response = slotService.createSlot(request, principal);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Slot created successfully"));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Create multiple slots",
            description = "Fill a day on your own calendar. Slots clashing with existing ones are skipped; "
                    + "a range where every slot clashes returns 409.")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> createBulkSlots(
            @Valid @RequestBody BulkSlotRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<SlotResponse> response = slotService.createBulkSlots(request, principal);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, response.size() + " slots created successfully"));
    }

    @GetMapping("/{slotId}")
    public ResponseEntity<ApiResponse<SlotResponse>> getSlotById(@PathVariable String slotId) {

        SlotResponse response = slotService.getSlotById(slotId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}")
    @Operation(summary = "Bookable slots for a doctor", description = "Unclaimed slots that have not already started")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAvailableSlotsByDoctor(
            @PathVariable String doctorId) {

        List<SlotResponse> response = slotService.getAvailableSlotsByDoctor(doctorId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/doctor/{doctorId}/date/{date}")
    @Operation(summary = "Bookable slots for a doctor on a date")
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
    @Operation(summary = "Every slot for a doctor on a date",
            description = "Includes booked and already-started slots — the doctor's own schedule view")
    public ResponseEntity<ApiResponse<List<SlotResponse>>> getAllSlotsByDoctorAndDate(
            @PathVariable String doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<SlotResponse> response = slotService.getAllSlotsByDoctorAndDate(doctorId, date);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{slotId}")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Delete one of your own slots")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(
            @PathVariable String slotId,
            @AuthenticationPrincipal UserPrincipal principal) {

        slotService.deleteSlot(slotId, principal);

        return ResponseEntity.ok(ApiResponse.success(null, "Slot deleted successfully"));
    }

    @DeleteMapping("/doctor/{doctorId}/date/{date}")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Clear a day on your own calendar")
    public ResponseEntity<ApiResponse<Void>> deleteSlotsByDoctorAndDate(
            @PathVariable String doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserPrincipal principal) {

        slotService.deleteSlotsByDoctorAndDate(doctorId, date, principal);

        return ResponseEntity.ok(ApiResponse.success(null, "Slots deleted successfully"));
    }
}
