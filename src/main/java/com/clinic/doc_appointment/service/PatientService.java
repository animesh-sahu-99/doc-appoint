package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.PatientUpdateRequest;
import com.clinic.doc_appointment.dto.response.PatientResponse;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.exception.InvalidStateException;
import com.clinic.doc_appointment.mapper.PatientMapper;
import com.clinic.doc_appointment.repository.AppointmentDocumentRepository;
import com.clinic.doc_appointment.repository.AppointmentRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import com.clinic.doc_appointment.repository.ReviewRepository;
import com.clinic.doc_appointment.security.AppointmentAccessGuard;
import com.clinic.doc_appointment.security.SelfAccessGuard;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.registration.RegistrationValidator;
import com.clinic.doc_appointment.util.EntityFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient profile reads and updates.
 *
 * <p>Registration lives in {@code service.registration.PatientRegistrationService} (Template
 * Method) and is reached only through {@code /api/auth/patient/register}. This class deliberately
 * has no register method: a second copy of that logic previously sat behind an authenticated
 * endpoint, which let any signed-in user create accounts.
 *
 * <p>Every method takes the calling principal, because a patient id in a path is a claim, not
 * proof. Reads use {@link AppointmentAccessGuard} so a doctor treating the patient can still see
 * the profile; writes use {@link SelfAccessGuard} because only the patient may change their own.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final AppointmentDocumentRepository documentRepository;
    private final PatientMapper patientMapper;
    private final RegistrationValidator registrationValidator;
    private final AppointmentAccessGuard appointmentAccessGuard;
    private final SelfAccessGuard selfAccessGuard;

    /**
     * The patient themselves, or a doctor who has at least one appointment with them — the doctor
     * case is what lets the appointment detail screen show who it is treating.
     */
    @Transactional(readOnly = true)
    public PatientResponse getPatientById(String patientId, UserPrincipal caller) {
        appointmentAccessGuard.assertCanViewPatientHistory(caller, patientId);
        return patientMapper.toResponse(findPatientById(patientId));
    }

    @Transactional
    public PatientResponse updatePatient(String patientId, PatientUpdateRequest request, UserPrincipal caller) {
        selfAccessGuard.assertPatientSelf(caller, patientId);
        log.info("Updating patient: {}", patientId);

        Patient patient = findPatientById(patientId);

        // Email uniqueness must be checked across BOTH tables, not just patients — see
        // RegistrationValidator.requireEmailAvailable for why a single-table check locks the user out.
        if (request.getEmail() != null && !request.getEmail().equals(patient.getEmail())) {
            registrationValidator.requireEmailAvailable(request.getEmail());
            patient.setEmail(request.getEmail());
        }

        if (request.getFirstName() != null) {
            patient.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            patient.setLastName(request.getLastName());
        }
        if (request.getGender() != null) {
            patient.setGender(request.getGender());
        }
        if (request.getDateOfBirth() != null) {
            patient.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getAddress() != null) {
            patient.setAddress(request.getAddress());
        }

        Patient updatedPatient = patientRepository.save(patient);
        log.info("Patient updated successfully: {}", patientId);

        return patientMapper.toResponse(updatedPatient);
    }

    /**
     * Hard-deletes a patient, but only one with no history.
     *
     * <p>{@code Patient.appointments} cascades {@code ALL} and {@code Appointment.payment} cascades
     * {@code ALL}, so an unguarded delete would silently take the clinic's appointment <em>and</em>
     * payment records with it, while leaving each freed slot stuck at {@code is_available = false}
     * forever. Reviews and documents do not cascade at all, so the same call would instead fail on a
     * foreign key and surface as an unexplained 409.
     *
     * <p>Refusing with a specific reason is the honest behaviour for both. Anonymising the record
     * instead of deleting it is the better long-term answer, but that is a schema change and a
     * product decision, not a bug fix.
     */
    @Transactional
    public void deletePatient(String patientId, UserPrincipal caller) {
        selfAccessGuard.assertPatientSelf(caller, patientId);
        log.info("Deleting patient: {}", patientId);

        Patient patient = findPatientById(patientId);

        if (appointmentRepository.existsByPatientPatientId(patientId)) {
            throw new InvalidStateException(
                    "This account has appointment history and cannot be deleted. "
                            + "Contact the clinic to have your records archived.");
        }
        if (reviewRepository.existsByPatientPatientId(patientId)) {
            throw new InvalidStateException(
                    "This account has reviews attached and cannot be deleted.");
        }
        if (documentRepository.existsByAppointmentPatientPatientId(patientId)) {
            throw new InvalidStateException(
                    "This account has medical documents attached and cannot be deleted.");
        }

        patientRepository.delete(patient);
        log.info("Patient deleted successfully: {}", patientId);
    }

    // =============== HELPER METHODS ===============

    private Patient findPatientById(String patientId) {
        return EntityFinder.findOrThrow(patientRepository, patientId, "Patient not found with ID: " + patientId);
    }
}
