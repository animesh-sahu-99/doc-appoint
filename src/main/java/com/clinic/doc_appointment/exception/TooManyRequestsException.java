package com.clinic.doc_appointment.exception;

/**
 * Thrown when a client exceeds an allowed request rate (e.g. too many failed
 * login attempts from one IP). Mapped to HTTP 429 Too Many Requests.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
