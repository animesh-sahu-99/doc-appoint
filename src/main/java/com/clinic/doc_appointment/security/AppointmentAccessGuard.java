package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.ForbiddenOperationException;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Centralized ownership/authorization checks for appointment access.
 *
 * <p>Coarse role gating (doctor-vs-patient) is handled declaratively via
 * {@code @PreAuthorize} on the controller. This guard enforces the fine-grained
 * "does this caller own / relate to this specific resource" rules that require the
 * caller's identity and, for mutations, the loaded {@link Appointment}. Every check
 * fails closed by throwing {@link ForbiddenOperationException} (HTTP 403).
 */
@Component
@RequiredArgsConstructor
public class AppointmentAccessGuard {

    private final AppointmentRepository appointmentRepository;

    private boolean isDoctor(UserPrincipal caller) {
        return Role.DOCTOR.authority().equals(caller.getRole());
    }

    private boolean isPatient(UserPrincipal caller) {
        return Role.PATIENT.authority().equals(caller.getRole());
    }

    private void deny() {
        throw new ForbiddenOperationException("You are not authorized to perform this action.");
    }

    /** True when the caller is either the appointment's own patient or its own doctor. */
    private boolean ownsAppointment(UserPrincipal caller, Appointment appointment) {
        boolean owningPatient = isPatient(caller)
                && appointment.getPatient().getPatientId().equals(caller.getId());
        boolean owningDoctor = isDoctor(caller)
                && appointment.getDoctor().getDoctorId().equals(caller.getId());
        return owningPatient || owningDoctor;
    }

    /**
     * A patient may read their own history; a doctor may read the history of a patient
     * they have at least one appointment with. Everyone else is denied.
     */
    public void assertCanViewPatientHistory(UserPrincipal caller, String patientId) {
        if (isPatient(caller) && caller.getId().equals(patientId)) {
            return;
        }
        if (isDoctor(caller)
                && appointmentRepository.existsByDoctorDoctorIdAndPatientPatientId(caller.getId(), patientId)) {
            return;
        }
        deny();
    }

    /** A doctor may only read their own schedule. */
    public void assertCanViewDoctorSchedule(UserPrincipal caller, String doctorId) {
        if (isDoctor(caller) && caller.getId().equals(doctorId)) {
            return;
        }
        deny();
    }

    /**
     * Reading one appointment (and anything derived from it, e.g. its payment or documents):
     * the owning patient or the owning doctor.
     *
     * <p>{@code AppointmentResponse} carries clinical {@code notes} and the patient's phone
     * number, and appointment numbers are guessable, so this must be checked on every read
     * path — not only on mutations.
     */
    public void assertCanViewAppointment(UserPrincipal caller, Appointment appointment) {
        if (ownsAppointment(caller, appointment)) {
            return;
        }
        deny();
    }

    /** Paying for an appointment: only the patient it belongs to. */
    public void assertOwnsAppointmentAsPatient(UserPrincipal caller, Appointment appointment) {
        if (isPatient(caller) && appointment.getPatient().getPatientId().equals(caller.getId())) {
            return;
        }
        deny();
    }

    /** confirm / complete / no-show / notes: only the appointment's own doctor. */
    public void assertOwnsAppointmentAsDoctor(UserPrincipal caller, Appointment appointment) {
        if (isDoctor(caller) && appointment.getDoctor().getDoctorId().equals(caller.getId())) {
            return;
        }
        deny();
    }

    /** cancel: the owning patient or the owning doctor. */
    public void assertCanCancel(UserPrincipal caller, Appointment appointment) {
        if (ownsAppointment(caller, appointment)) {
            return;
        }
        deny();
    }
}
