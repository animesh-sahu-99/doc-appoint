package com.clinic.doc_appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    /** When true, ends every session the owner holds rather than just this device's. */
    private boolean allDevices = false;
}
