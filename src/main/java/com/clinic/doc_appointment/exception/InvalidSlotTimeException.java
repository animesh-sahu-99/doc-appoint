package com.clinic.doc_appointment.exception;

public class InvalidSlotTimeException extends RuntimeException {
    public InvalidSlotTimeException(String message) {
        super(message);
    }
}
