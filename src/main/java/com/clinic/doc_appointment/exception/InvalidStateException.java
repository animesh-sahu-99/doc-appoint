package com.clinic.doc_appointment.exception;

/**
 * Thrown when an operation is attempted on a resource in an invalid state.
 * E.g., trying to confirm an already-cancelled appointment.
 */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) {
        super(message);
    }
}
