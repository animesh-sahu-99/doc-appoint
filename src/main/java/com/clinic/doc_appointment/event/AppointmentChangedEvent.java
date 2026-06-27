package com.clinic.doc_appointment.event;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Published by {@code AppointmentService} when an appointment is booked or transitions. Carries an
 * immutable snapshot (never the managed entity) so listeners can compose notifications without
 * touching the persistence context.
 *
 * <p>Consumed after the appointment transaction commits
 * ({@code @TransactionalEventListener(AFTER_COMMIT)}), so an event from a rolled-back/retried booking
 * attempt is simply discarded — see {@code DESIGN_PATTERNS.md} §6.
 */
public record AppointmentChangedEvent(
        Kind kind,
        String appointmentId,
        String patientId,
        String doctorId,
        String patientFirstName,
        String doctorLastName,
        LocalDate slotDate,
        LocalTime startTime) {

    public enum Kind { BOOKED, CONFIRMED, CANCELLED, COMPLETED, NOTES_UPDATED }
}
