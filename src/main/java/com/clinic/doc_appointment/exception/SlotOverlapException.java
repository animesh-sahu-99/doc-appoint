package com.clinic.doc_appointment.exception;

public class SlotOverlapException extends RuntimeException {
    public SlotOverlapException(String message) {
        super(message);
    }
}
