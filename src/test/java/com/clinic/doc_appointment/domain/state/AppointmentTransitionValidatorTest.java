package com.clinic.doc_appointment.domain.state;

import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppointmentTransitionValidatorTest {

    private final AppointmentTransitionValidator validator = new AppointmentTransitionValidator();

    @Test
    void confirmOnlyFromPending() {
        assertDoesNotThrow(() -> validator.assertCanConfirm(AppointmentStatus.PENDING));
        assertThrows(InvalidStateException.class, () -> validator.assertCanConfirm(AppointmentStatus.CONFIRMED));
        assertThrows(InvalidStateException.class, () -> validator.assertCanConfirm(AppointmentStatus.COMPLETED));
    }

    @Test
    void completeOnlyFromConfirmed() {
        assertDoesNotThrow(() -> validator.assertCanComplete(AppointmentStatus.CONFIRMED));
        assertThrows(InvalidStateException.class, () -> validator.assertCanComplete(AppointmentStatus.PENDING));
        assertThrows(InvalidStateException.class, () -> validator.assertCanComplete(AppointmentStatus.COMPLETED));
    }

    @Test
    void cancelBlockedFromCancelledAndCompletedOnly() {
        assertDoesNotThrow(() -> validator.assertCanCancel(AppointmentStatus.PENDING));
        assertDoesNotThrow(() -> validator.assertCanCancel(AppointmentStatus.CONFIRMED));
        assertDoesNotThrow(() -> validator.assertCanCancel(AppointmentStatus.NO_SHOW)); // preserved: currently allowed
        assertThrows(InvalidStateException.class, () -> validator.assertCanCancel(AppointmentStatus.CANCELLED));
        assertThrows(InvalidStateException.class, () -> validator.assertCanCancel(AppointmentStatus.COMPLETED));
    }

    @Test
    void noShowBlockedFromCancelledAndCompletedOnly() {
        assertDoesNotThrow(() -> validator.assertCanMarkNoShow(AppointmentStatus.PENDING));
        assertDoesNotThrow(() -> validator.assertCanMarkNoShow(AppointmentStatus.CONFIRMED));
        assertThrows(InvalidStateException.class, () -> validator.assertCanMarkNoShow(AppointmentStatus.CANCELLED));
        assertThrows(InvalidStateException.class, () -> validator.assertCanMarkNoShow(AppointmentStatus.COMPLETED));
    }

    @Test
    void notesBlockedFromCancelledAndNoShowOnly() {
        assertDoesNotThrow(() -> validator.assertCanEditNotes(AppointmentStatus.PENDING));
        assertDoesNotThrow(() -> validator.assertCanEditNotes(AppointmentStatus.CONFIRMED));
        assertDoesNotThrow(() -> validator.assertCanEditNotes(AppointmentStatus.COMPLETED));
        assertThrows(InvalidStateException.class, () -> validator.assertCanEditNotes(AppointmentStatus.CANCELLED));
        assertThrows(InvalidStateException.class, () -> validator.assertCanEditNotes(AppointmentStatus.NO_SHOW));
    }
}
