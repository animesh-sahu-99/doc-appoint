package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.DoctorReplyRequest;
import com.clinic.doc_appointment.dto.request.ReviewRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.ReviewResponse;
import com.clinic.doc_appointment.enums.ReviewSort;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reviews", description = "Doctor review APIs")
public class ReviewController {

    /** Hard ceiling on a page. Without one, size=1000000 went straight into PageRequest.of. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReviewRequest request) {
        
        ReviewResponse response = reviewService.submitReview(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Review submitted successfully"));
    }

    @PostMapping("/{reviewId}/reply")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<ReviewResponse>> replyToReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String reviewId,
            @Valid @RequestBody DoctorReplyRequest request) {

        ReviewResponse response = reviewService.replyToReview(principal.getId(), reviewId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Reply submitted successfully"));
    }

    @GetMapping("/doctor/{doctorId}")
    @Operation(summary = "List a doctor's reviews", description = "sort: recent (default), high or low")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getDoctorReviews(
            @PathVariable String doctorId,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page cannot be negative") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed " + MAX_PAGE_SIZE) int size) {

        Page<ReviewResponse> response =
                reviewService.getDoctorReviews(doctorId, ReviewSort.fromRequest(sort), page, size);
        return ResponseEntity.ok(ApiResponse.success(response, "Reviews fetched successfully"));
    }
}
