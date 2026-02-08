// exception/BookingConflictException.java
package com.clinic.doc_appointment.exception;

public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}