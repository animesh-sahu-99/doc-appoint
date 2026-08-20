package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.dto.request.DoctorUpdateRequest;
import com.clinic.doc_appointment.entity.Appointment;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.DoctorAvailability;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.entity.Review;
import com.clinic.doc_appointment.enums.AppointmentStatus;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.enums.Specialization;
import com.clinic.doc_appointment.mapper.DoctorMapper;
import com.clinic.doc_appointment.security.SelfAccessGuard;
import com.clinic.doc_appointment.security.UserPrincipal;
import com.clinic.doc_appointment.service.DoctorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two bulk-update paths on {@code doctors}, exercised against a real database.
 *
 * <p>Both defects they guard are invisible to a mocked repository: one is about what the persistence
 * context does after a bulk statement writes around it, the other about the statement being a single
 * atomic UPDATE rather than a read-modify-write.
 */
@DataJpaTest
class DoctorRepositoryBulkUpdateTest {

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Doctor doctor;
    private Patient patient;

    @BeforeEach
    void setUp() {
        doctor = doctorRepository.save(new Doctor()
                .setFirstName("Anjali")
                .setLastName("Sharma")
                .setEmail("anjali@clinic.test")
                .setCountryCode("+91")
                .setPhoneNumber("9876543210")
                .setPassword("hash")
                .setSpecialization(Specialization.CARDIOLOGIST)
                .setQualification("MBBS, MD")
                .setExperienceYears(12)
                .setConsultationFee(new BigDecimal("900.00"))
                .setIsActive(true));

        patient = entityManager.persist(new Patient()
                .setFirstName("Rahul")
                .setLastName("Verma")
                .setCountryCode("+91")
                .setPhoneNumber("9000000001")
                .setPassword("hash"));

        entityManager.flush();
    }

    /**
     * A bulk JPQL update writes around the persistence context. Without
     * {@code clearAutomatically = true} the {@code findById} that follows returned the instance
     * loaded <em>before</em> the update, so {@code PUT /api/doctors/{id}} answered 200 while echoing
     * the caller's old consultation fee back at them — indistinguishable from the update failing.
     */
    @Test
    void profileUpdateIsVisibleToAReadInTheSameTransaction() {
        // Put the entity in the persistence context first; this is what the stale read depended on.
        Doctor loadedBefore = doctorRepository.findById(doctor.getDoctorId()).orElseThrow();
        assertThat(loadedBefore.getConsultationFee()).isEqualByComparingTo("900.00");

        int updated = doctorRepository.updateProfileFields(
                doctor.getDoctorId(), "Anjali S", "MBBS, MD, DM", 15, new BigDecimal("1500.00"), "Updated bio");

        assertThat(updated).isEqualTo(1);

        Doctor reread = doctorRepository.findById(doctor.getDoctorId()).orElseThrow();
        assertThat(reread.getConsultationFee()).isEqualByComparingTo("1500.00");
        assertThat(reread.getFirstName()).isEqualTo("Anjali S");
        assertThat(reread.getExperienceYears()).isEqualTo(15);
    }

    /** The service returns the DTO the client sees, so assert the fix end-to-end through it. */
    @Test
    void updateDoctorRespondsWithTheNewValues() {
        DoctorService service = new DoctorService(doctorRepository, new DoctorMapper(), new SelfAccessGuard());
        UserPrincipal caller = new UserPrincipal(
                doctor.getDoctorId(), doctor.getEmail(), doctor.getPassword(), Role.DOCTOR.authority());

        doctorRepository.findById(doctor.getDoctorId());   // prime the persistence context

        DoctorUpdateRequest request = new DoctorUpdateRequest();
        request.setConsultationFee(new BigDecimal("1500.00"));

        var response = service.updateDoctor(doctor.getDoctorId(), request, caller);

        assertThat(response.getConsultationFee()).isEqualByComparingTo("1500.00");
    }

    /** A blank field means "leave this alone", not "overwrite with blank". */
    @Test
    void profileUpdateLeavesOmittedFieldsUntouched() {
        doctorRepository.updateProfileFields(
                doctor.getDoctorId(), null, null, null, new BigDecimal("1200.00"), null);

        Doctor reread = doctorRepository.findById(doctor.getDoctorId()).orElseThrow();
        assertThat(reread.getConsultationFee()).isEqualByComparingTo("1200.00");
        assertThat(reread.getFirstName()).isEqualTo("Anjali");
        assertThat(reread.getQualification()).isEqualTo("MBBS, MD");
        assertThat(reread.getExperienceYears()).isEqualTo(12);
    }

    @Test
    void profileUpdateReportsZeroRowsForAnUnknownDoctor() {
        assertThat(doctorRepository.updateProfileFields(
                "DOC-does-not-exist", "X", null, null, null, null)).isZero();
    }

    /**
     * Rating stats are recomputed by one statement the database evaluates, so two reviews cannot
     * produce a lost update. The previous count-then-average-then-save was a read-modify-write on a
     * row with no {@code @Version}: concurrent reviewers each saw only their own uncommitted insert,
     * both wrote N+1, and the second silently overwrote the first.
     *
     * <p>This also proves the JPQL sub-selects in the UPDATE actually execute — the reason to test it
     * against a database rather than a mock.
     */
    @Test
    void ratingStatsAreRecomputedFromTheReviewRows() {
        persistReview(5);
        persistReview(4);
        persistReview(3);

        int updated = doctorRepository.recomputeRatingStats(doctor.getDoctorId());
        assertThat(updated).isEqualTo(1);

        Doctor reread = doctorRepository.findById(doctor.getDoctorId()).orElseThrow();
        assertThat(reread.getTotalReviews()).isEqualTo(3);
        assertThat(reread.getAverageRating()).isEqualTo(4.0);   // (5 + 4 + 3) / 3
    }

    /** Every recompute derives from source, so adding a review never compounds a rounded value. */
    @Test
    void ratingStatsStayConsistentAsReviewsAccumulate() {
        persistReview(5);
        doctorRepository.recomputeRatingStats(doctor.getDoctorId());
        assertThat(doctorRepository.findById(doctor.getDoctorId()).orElseThrow().getTotalReviews()).isEqualTo(1);

        persistReview(2);
        doctorRepository.recomputeRatingStats(doctor.getDoctorId());

        Doctor reread = doctorRepository.findById(doctor.getDoctorId()).orElseThrow();
        assertThat(reread.getTotalReviews()).isEqualTo(2);
        assertThat(reread.getAverageRating()).isEqualTo(3.5);
    }

    @Test
    void ratingStatsFallBackToZeroWhenThereAreNoReviews() {
        doctorRepository.recomputeRatingStats(doctor.getDoctorId());

        Doctor reread = doctorRepository.findById(doctor.getDoctorId()).orElseThrow();
        assertThat(reread.getTotalReviews()).isZero();
        assertThat(reread.getAverageRating()).isEqualTo(0.0);
    }

    private void persistReview(int rating) {
        DoctorAvailability slot = entityManager.persist(new DoctorAvailability()
                .setDoctor(doctor)
                .setSlotDate(LocalDate.now().minusDays(1))
                .setStartTime(LocalTime.of(9, 0).plusMinutes(rating * 30L))
                .setEndTime(LocalTime.of(9, 30).plusMinutes(rating * 30L))
                .setDurationMinutes(30)
                .setIsAvailable(false));

        Appointment appointment = entityManager.persist(new Appointment()
                .setAppointmentNumber("APT-TEST-" + rating)
                .setDoctor(doctor)
                .setPatient(patient)
                .setSlot(slot)
                .setStatus(AppointmentStatus.COMPLETED));

        reviewRepository.save(new Review()
                .setAppointment(appointment)
                .setDoctor(doctor)
                .setPatient(patient)
                .setRating(rating)
                .setComment("Rated " + rating));
    }
}
