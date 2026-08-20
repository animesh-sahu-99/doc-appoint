package com.clinic.doc_appointment.controller;

import com.clinic.doc_appointment.dto.request.DeviceTokenRequest;
import com.clinic.doc_appointment.dto.response.ApiResponse;
import com.clinic.doc_appointment.dto.response.NotificationResponse;
import com.clinic.doc_appointment.service.NotificationService;
import com.clinic.doc_appointment.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "Notification management APIs")
@Validated
public class NotificationController {

    /** Hard ceiling on a page. Without one, size=1000000 went straight into PageRequest.of. */
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    @Operation(summary = "Get user notifications matching the authenticated user")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getUserNotifications(
            @AuthenticationPrincipal UserPrincipal userDetails,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page cannot be negative") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed " + MAX_PAGE_SIZE) int size
    ) {
        String userId = userDetails.getId();
        Page<NotificationResponse> notifications = notificationService.getUserNotifications(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(notifications, "Notifications fetched successfully"));
    }

    @Operation(summary = "Register FCM token for push notifications")
    @PostMapping("/device-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> registerDeviceToken(
            @Valid @RequestBody DeviceTokenRequest request,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        notificationService.registerDeviceToken(userDetails.getId(), request.getFcmToken(), request.getDeviceType());
        return ResponseEntity.ok(ApiResponse.success(null, "Device token registered successfully"));
    }

    @Operation(summary = "Get unread message count for the authenticated user")
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        int count = notificationService.getUnreadCount(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count), "Unread count fetched"));
    }

    @Operation(summary = "Mark a specific notification as read")
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        notificationService.markAsRead(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Notification marked as read"));
    }

    @Operation(summary = "Mark all notifications as read for the authenticated user")
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        notificationService.markAllAsRead(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "All notifications marked as read"));
    }
}
