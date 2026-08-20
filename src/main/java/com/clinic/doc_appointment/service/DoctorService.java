package com.clinic.doc_appointment.service;

import com.clinic.doc_appointment.dto.request.DoctorFilterRequest;
import com.clinic.doc_appointment.dto.request.DoctorUpdateRequest;
import com.clinic.doc_appointment.dto.response.DoctorResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Specialization;
import com.clinic.doc_appointment.exception.ResourceNotFoundException;
import com.clinic.doc_appointment.mapper.DoctorMapper;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.security.SelfAccessGuard;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.specification.DoctorSpecification;
import com.clinic.doc_appointment.util.EntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Doctor discovery and profile maintenance.
 *
 * <p>Registration lives in {@code service.registration.DoctorRegistrationService} and is reached
 * only through the public {@code /api/auth/doctor/register}. The duplicate that used to sit here
 * was reachable at {@code POST /api/doctors/register}, which — because only {@code /api/auth/**} is
 * public — required a token rather than being open, and so let any signed-in patient create doctor
 * accounts.
 *
 * <p>Discovery reads are intentionally open to any authenticated user; only {@link #updateDoctor}
 * is owner-scoped.
 */
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;
    private final SelfAccessGuard selfAccessGuard;

    @Transactional(readOnly = true)
    public DoctorResponse getDoctorById(String doctorId) {
        Doctor doctor = EntityFinder.findOrThrow(doctorRepository, doctorId, "Doctor not found with id: " + doctorId);
        return doctorMapper.toResponse(doctor);
    }

    /**
     * Updates the calling doctor's own profile.
     *
     * <p>The single bulk statement is both the update and the existence check: a zero row count
     * means there is nothing to update. The previous {@code findById} guard before the update was
     * worse than redundant — it pulled the entity into the persistence context, and because the
     * bulk statement writes around that context, the re-read afterwards returned the untouched
     * instance and the response echoed the caller's old fee back at them.
     */
    @Transactional
    public DoctorResponse updateDoctor(String doctorId, DoctorUpdateRequest request, UserPrincipal caller) {
        selfAccessGuard.assertDoctorSelf(caller, doctorId);

        int updated = doctorRepository.updateProfileFields(
                doctorId,
                blankToNull(request.getName()),
                blankToNull(request.getQualification()),
                request.getExperienceYears(),
                request.getConsultationFee(),
                request.getAbout());

        if (updated == 0) {
            throw new ResourceNotFoundException("Doctor not found with id: " + doctorId);
        }

        Doctor refreshed = EntityFinder.findOrThrow(doctorRepository, doctorId,
                "Doctor not found with id: " + doctorId);
        return doctorMapper.toResponse(refreshed);
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> getAllDoctors() {
        return doctorMapper.toResponseList(doctorRepository.findByIsActiveTrue());
    }

    /**
     * Dynamic multi-filter search. All filter params are optional.
     * Falls back to "all active doctors" when no filters are set.
     */
    @Transactional(readOnly = true)
    public List<DoctorResponse> searchDoctors(DoctorFilterRequest filters) {
        Specification<Doctor> spec = DoctorSpecification.withFilters(filters);
        return doctorMapper.toResponseList(doctorRepository.findAll(spec));
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> getDoctorsBySpecialization(Specialization specialization) {
        return doctorMapper.toResponseList(doctorRepository.findBySpecializationAndIsActiveTrue(specialization));
    }

    /** Pure enum projection for the client's dropdown — touches no database. */
    public List<SpecializationInfo> getAllSpecializations() {
        return Arrays.stream(Specialization.values())
                .map(spec -> new SpecializationInfo(
                        spec.name(),
                        spec.getDisplayName(),
                        spec.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> getAvailableDoctorsBySpecialization(Specialization specialization) {
        return doctorMapper.toResponseList(doctorRepository.findAvailableDoctorsBySpecialization(specialization));
    }

    /** A blank incoming value means "leave this field alone", which the update query encodes as null. */
    private static String blankToNull(String value) {
        return (value != null && !value.isBlank()) ? value : null;
    }

    // Inner class for specialization info
    public record SpecializationInfo(String code, String displayName, String description) {}
}
