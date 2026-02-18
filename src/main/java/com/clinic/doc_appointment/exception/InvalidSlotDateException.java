package com.clinic.doc_appointment.exception;

public class InvalidSlotDateException extends RuntimeException {
    public InvalidSlotDateException(String message) {
        super(message);
    }
}
