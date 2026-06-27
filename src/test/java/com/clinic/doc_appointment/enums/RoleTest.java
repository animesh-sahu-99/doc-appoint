package com.clinic.doc_appointment.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleTest {

    @Test
    void authorityKeepsExactSpringSecurityStrings() {
        // These strings gate @PreAuthorize("hasRole('...')") and are embedded in issued JWTs.
        assertEquals("ROLE_DOCTOR", Role.DOCTOR.authority());
        assertEquals("ROLE_PATIENT", Role.PATIENT.authority());
    }

    @Test
    void fromAuthorityParsesPrefixedForm() {
        assertEquals(Role.DOCTOR, Role.fromAuthority("ROLE_DOCTOR"));
        assertEquals(Role.PATIENT, Role.fromAuthority("ROLE_PATIENT"));
    }

    @Test
    void fromAuthorityParsesBareAndMixedCase() {
        assertEquals(Role.DOCTOR, Role.fromAuthority("DOCTOR"));
        assertEquals(Role.PATIENT, Role.fromAuthority("patient"));
    }

    @Test
    void authorityRoundTripsThroughFromAuthority() {
        assertEquals(Role.DOCTOR, Role.fromAuthority(Role.DOCTOR.authority()));
        assertEquals(Role.PATIENT, Role.fromAuthority(Role.PATIENT.authority()));
    }

    @Test
    void fromAuthorityRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Role.fromAuthority(null));
    }
}
