package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.ForbiddenOperationException;
import org.springframework.stereotype.Component;

/**
 * "Is this caller acting on their own account?" — the check that keeps a logged-in user from
 * reaching another user's profile or calendar by swapping an id in the path or request body.
 *
 * <p>Deliberately separate from {@link AppointmentAccessGuard}: this one needs no repository and
 * no loaded entity, only the principal and the claimed id. Both fail closed the same way, by
 * throwing {@link ForbiddenOperationException} (HTTP 403).
 *
 * <p>The role is checked as well as the id, because doctor and patient ids come from different
 * tables and an id match alone would not prove the caller is the right kind of user.
 */
@Component
public class SelfAccessGuard {

    /** The caller must be the doctor whose resource this is. */
    public void assertDoctorSelf(UserPrincipal caller, String doctorId) {
        assertSelf(caller, Role.DOCTOR, doctorId);
    }

    /** The caller must be the patient whose resource this is. */
    public void assertPatientSelf(UserPrincipal caller, String patientId) {
        assertSelf(caller, Role.PATIENT, patientId);
    }

    private void assertSelf(UserPrincipal caller, Role required, String resourceOwnerId) {
        if (caller != null
                && required.authority().equals(caller.getRole())
                && caller.getId() != null
                && caller.getId().equals(resourceOwnerId)) {
            return;
        }
        throw new ForbiddenOperationException("You are not authorized to perform this action.");
    }
}
