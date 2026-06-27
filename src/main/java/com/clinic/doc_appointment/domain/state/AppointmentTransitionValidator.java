package com.clinic.doc_appointment.domain.state;

import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.exception.InvalidStateException;
import org.springframework.stereotype.Component;

/**
 * Centralizes the appointment lifecycle guard rules — the State-pattern intent (state-dependent
 * behavior in one place) kept KISS for five states, rather than a class per state.
 *
 * <p>Each method preserves the exact {@link InvalidStateException} message the previous inline
 * checks in {@code AppointmentService} used. Notes editing is modeled as a guard, not a transition.
 */
@Component
public class AppointmentTransitionValidator {

    public void assertCanConfirm(AppointmentStatus current) {
        if (current != AppointmentStatus.PENDING) {
            throw new InvalidStateException("Only pending appointments can be confirmed");
        }
    }

    public void assertCanComplete(AppointmentStatus current) {
        if (current != AppointmentStatus.CONFIRMED) {
            throw new InvalidStateException("Only confirmed appointments can be completed");
        }
    }

    public void assertCanCancel(AppointmentStatus current) {
        if (current == AppointmentStatus.CANCELLED) {
            throw new InvalidStateException("Appointment is already cancelled");
        }
        if (current == AppointmentStatus.COMPLETED) {
            throw new InvalidStateException("Cannot cancel a completed appointment");
        }
    }

    public void assertCanMarkNoShow(AppointmentStatus current) {
        if (current == AppointmentStatus.CANCELLED || current == AppointmentStatus.COMPLETED) {
            throw new InvalidStateException("Cannot mark cancelled/completed appointment as no-show");
        }
    }

    public void assertCanEditNotes(AppointmentStatus current) {
        if (current == AppointmentStatus.CANCELLED || current == AppointmentStatus.NO_SHOW) {
            throw new InvalidStateException("Cannot add notes to a cancelled or no-show appointment.");
        }
    }
}
