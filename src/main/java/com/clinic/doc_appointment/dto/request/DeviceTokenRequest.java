package com.clinic.doc_appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceTokenRequest {
    @NotBlank(message = "FCM token is required")
    private String fcmToken;
    
    private String deviceType; // Optional: ANDROID, IOS
}
