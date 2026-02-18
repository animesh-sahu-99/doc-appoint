package com.clinic.doc_appointment.exception;

public class BookedSlotException extends RuntimeException {
    public BookedSlotException(String message) {
        super(message);
    }
}
