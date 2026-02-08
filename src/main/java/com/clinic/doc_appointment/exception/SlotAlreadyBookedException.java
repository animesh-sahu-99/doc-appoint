// exception/SlotAlreadyBookedException.java
package com.clinic.doc_appointment.exception;

public class SlotAlreadyBookedException extends RuntimeException {
    public SlotAlreadyBookedException(String message) {
        super(message);
    }
}