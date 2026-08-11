package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.dto.response.AuthResponse;

/**
 * An access token paired with the refresh token that renews it, plus both lifetimes in seconds.
 *
 * <p>{@link #decorate} exists so the four call sites that build an {@link AuthResponse} — two
 * logins, two registrations — cannot drift apart on which token fields they populate.
 */
public record IssuedTokens(String accessToken,
                           String refreshToken,
                           long expiresIn,
                           long refreshExpiresIn) {

    /** Fills in every token-related field, leaving identity fields to the caller. */
    public AuthResponse.AuthResponseBuilder decorate(AuthResponse.AuthResponseBuilder builder) {
        return builder
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .refreshExpiresIn(refreshExpiresIn);
    }
}
