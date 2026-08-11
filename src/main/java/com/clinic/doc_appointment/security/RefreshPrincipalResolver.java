package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.exception.InvalidRefreshTokenException;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the security principal behind a stored refresh token.
 *
 * <p>Parallel to {@link CustomUserDetailsService}, but keyed by primary key + role rather than
 * by email. That choice is deliberate:
 * <ul>
 *   <li>{@code Patient.email} is nullable and mutable, so it is not a stable identity;</li>
 *   <li>the id prefix cannot substitute for the role — {@code IdPrefix.DOCTOR} and
 *       {@code IdPrefix.DOCUMENT} are both {@code "DOC-"} by documented design;</li>
 *   <li>the primary key is assigned once in {@code @PrePersist} and never changes.</li>
 * </ul>
 *
 * <p>Reading the live entity on every refresh also means a deleted account stops refreshing
 * immediately rather than at its token's natural expiry.
 */
@Component
@RequiredArgsConstructor
public class RefreshPrincipalResolver {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    /** The principal plus the display name the auth response needs, from a single lookup. */
    public record ResolvedUser(UserPrincipal principal, String displayName) {
    }

    /**
     * @throws InvalidRefreshTokenException if the owning account no longer exists. Deliberately
     *         not {@code ResourceNotFoundException} — a public endpoint must not turn a stale
     *         token into a 404 that confirms which accounts have been deleted.
     */
    public ResolvedUser resolve(String userId, Role role) {
        return switch (role) {
            case DOCTOR -> doctorRepository.findById(userId)
                    .map(doctor -> new ResolvedUser(
                            new UserPrincipal(doctor.getDoctorId(), doctor.getEmail(),
                                    doctor.getPassword(), Role.DOCTOR.authority()),
                            doctor.getFirstName()))
                    .orElseThrow(() -> new InvalidRefreshTokenException("owner not found: " + userId));

            case PATIENT -> patientRepository.findById(userId)
                    .map(patient -> new ResolvedUser(
                            new UserPrincipal(patient.getPatientId(), patient.getEmail(),
                                    patient.getPassword(), Role.PATIENT.authority()),
                            patient.getFirstName()))
                    .orElseThrow(() -> new InvalidRefreshTokenException("owner not found: " + userId));
        };
    }
}
