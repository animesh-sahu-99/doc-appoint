package com.clinic.doc_appointment.service.registration;

import com.clinic.doc_appointment.exception.DuplicateResourceException;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class RegistrationValidatorTest {

    private final DoctorRepository doctorRepository = Mockito.mock(DoctorRepository.class);
    private final PatientRepository patientRepository = Mockito.mock(PatientRepository.class);
    private final RegistrationValidator validator =
            new RegistrationValidator(doctorRepository, patientRepository);

    @Test
    void doctorRegistrationRejectsEmailAlreadyUsedByAPatient() {
        when(doctorRepository.existsByEmail("a@x.com")).thenReturn(false);
        when(patientRepository.existsByEmail("a@x.com")).thenReturn(true); // owned by a patient
        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> validator.validateDoctorRegistration("a@x.com", "+91", "999"));
        assertEquals("Email already registered", ex.getMessage());
    }

    @Test
    void patientRegistrationRejectsEmailAlreadyUsedByADoctor() {
        when(patientRepository.existsByEmail("a@x.com")).thenReturn(false);
        when(doctorRepository.existsByEmail("a@x.com")).thenReturn(true); // owned by a doctor
        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> validator.validatePatientRegistration("a@x.com", "+91", "999"));
        assertEquals("Email already registered", ex.getMessage());
    }

    @Test
    void patientRegistrationSkipsEmailCheckWhenBlank() {
        assertDoesNotThrow(() -> validator.validatePatientRegistration("  ", "+91", "999"));
    }

    @Test
    void doctorRegistrationRejectsDuplicatePhoneWithAlignedMessage() {
        when(doctorRepository.existsByCountryCodeAndPhoneNumber("+91", "999")).thenReturn(true);
        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> validator.validateDoctorRegistration("a@x.com", "+91", "999"));
        assertEquals("Phone number already registered", ex.getMessage());
    }

    @Test
    void acceptsFreshDoctorRegistration() {
        assertDoesNotThrow(() -> validator.validateDoctorRegistration("a@x.com", "+91", "999"));
    }
}
