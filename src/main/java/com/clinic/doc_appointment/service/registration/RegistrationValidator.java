package com.clinic.doc_appointment.service.registration;

import com.clinic.doc_appointment.exception.DuplicateResourceException;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for registration uniqueness rules, shared by every registration path:
 * the {@code /auth/*} Template-Method services and the {@code /doctors|/patients/register} services.
 *
 * <p><strong>Email must be unique ACROSS both the doctor and patient tables</strong> — login is by
 * email and {@code CustomUserDetailsService} resolves it across both tables, so an email owned by a
 * doctor must not also be usable by a patient (and vice-versa). Phone is unique only within the same
 * role's table (the entities' unique constraints are per-table, and login never uses phone).
 */
@Component
@RequiredArgsConstructor
public class RegistrationValidator {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    /** Doctor registration: email cross-table unique; phone unique among doctors. */
    public void validateDoctorRegistration(String email, String countryCode, String phoneNumber) {
        requireEmailAvailable(email);
        if (doctorRepository.existsByCountryCodeAndPhoneNumber(countryCode, phoneNumber)) {
            throw new DuplicateResourceException("Phone number already registered");
        }
    }

    /** Patient registration: email cross-table unique when present (optional); phone unique among patients. */
    public void validatePatientRegistration(String email, String countryCode, String phoneNumber) {
        requireEmailAvailable(email);
        if (patientRepository.existsByCountryCodeAndPhoneNumber(countryCode, phoneNumber)) {
            throw new DuplicateResourceException("Phone number already registered");
        }
    }

    /**
     * Email must not already exist in EITHER table. A blank/absent email is allowed (patients).
     *
     * <p>Public because profile <em>updates</em> need exactly the same rule as registration. A
     * single-table check there would let a patient claim an email a doctor already owns, and since
     * {@code CustomUserDetailsService} resolves doctors first, that patient could never log in
     * again — a lockout surfacing only as "Invalid email or password".
     */
    public void requireEmailAvailable(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (doctorRepository.existsByEmail(email) || patientRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already registered");
        }
    }
}
