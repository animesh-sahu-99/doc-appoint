package com.clinic.doc_appointment.exception;

/**
 * Thrown when an authenticated caller attempts an operation they are not
 * authorized to perform (e.g., reading another patient's history, or acting
 * on an appointment they do not own). Mapped to HTTP 403 Forbidden.
 */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
