package com.clinic.doc_appointment.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Small helpers for extracting information from the incoming HTTP request.
 */
public final class RequestUtils {

    private RequestUtils() {
    }

    /**
     * Resolves the client IP.
     *
     * <p>By default returns the real TCP peer ({@code getRemoteAddr()}).
     * {@code X-Forwarded-For} is honoured only when {@code trustForwardedFor} is true —
     * the header is client-controlled and, if trusted blindly, an attacker could rotate
     * fake values to bypass a per-IP limit. Enable it only behind a trusted proxy.
     */
    public static String getClientIp(HttpServletRequest request, boolean trustForwardedFor) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // First hop is the originating client.
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
