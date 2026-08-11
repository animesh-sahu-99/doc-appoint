package com.clinic.doc_appointment.exception;

/**
 * Thrown when a refresh token cannot be exchanged — unknown, expired, revoked, replayed after
 * consumption, past its family's absolute cap, or owned by an account that no longer exists.
 * Mapped to HTTP 401.
 *
 * <p>The message is an internal reason for the server log only. Every one of these cases returns
 * the same opaque message to the caller, so a client cannot probe which tokens exist or learn
 * that its replay was detected.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
