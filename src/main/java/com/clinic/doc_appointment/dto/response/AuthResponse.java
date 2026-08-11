package com.clinic.doc_appointment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Credentials and identity returned by login, registration and token refresh.
 *
 * <p>{@code token} is and remains the <em>access</em> token — the refresh fields were added
 * alongside it, so no existing field changed meaning. Nulls are omitted so a client built against
 * the pre-refresh shape never sees unexpected keys.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    /** Short-lived access token; send as {@code Authorization: Bearer <token>}. */
    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    /** Long-lived, opaque. Exchange at {@code POST /api/auth/refresh}; rotated on every use. */
    private String refreshToken;

    /** Access-token lifetime in seconds — lets the client refresh before expiry rather than after a 401. */
    private Long expiresIn;

    /** Refresh-token lifetime in seconds. */
    private Long refreshExpiresIn;

    private String role;      // ROLE_DOCTOR or ROLE_PATIENT
    private String userId;    // doctorId or patientId
    private String email;
    private String name;
}
