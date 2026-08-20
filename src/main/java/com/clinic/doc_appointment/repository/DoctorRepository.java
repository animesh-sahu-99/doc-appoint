package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Specialization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, String>, JpaSpecificationExecutor<Doctor> {

    Optional<Doctor> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCountryCodeAndPhoneNumber(String countryCode, String phoneNumber);

    List<Doctor> findBySpecializationAndIsActiveTrue(Specialization specialization);

    List<Doctor> findByIsActiveTrue();

    /**
     * Doctors of a specialization who still have a free slot.
     *
     * <p>The {@code slotDate >= CURRENT_DATE} predicate is load-bearing: without it a doctor whose
     * only unbooked slots are in the past was listed as available, and the client could then fetch
     * and book one of those past slots.
     */
    @Query("SELECT DISTINCT d FROM Doctor d " +
            "JOIN d.availabilitySlots s " +
            "WHERE d.specialization = :specialization " +
            "AND s.isAvailable = true " +
            "AND s.slotDate >= CURRENT_DATE " +
            "AND d.isActive = true")
    List<Doctor> findAvailableDoctorsBySpecialization(Specialization specialization);

    /**
     * Recomputes a doctor's denormalized rating stats from the {@code Review} rows, in one statement.
     *
     * <p>This has to be a single UPDATE rather than count-then-average-then-save. {@code Doctor}
     * carries no {@code @Version}, so the read-modify-write it replaces was a lost update: under READ
     * COMMITTED two patients reviewing the same doctor at once each saw only their own uncommitted
     * insert, both computed a total of N+1, and the second commit silently overwrote the first —
     * leaving the stored count and average permanently disagreeing with the rows they summarise.
     *
     * <p>Letting the database evaluate both sub-selects inside the UPDATE removes the window
     * entirely. {@code flushAutomatically} is what makes the just-saved review visible to them.
     *
     * <p>A {@code @Version} on {@code Doctor} would <em>not</em> have been the right fix: the profile
     * update above is bulk JPQL, which does not bump a version column, so adding one would have
     * introduced stale-version failures elsewhere.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Doctor d SET " +
            "d.totalReviews = (SELECT COUNT(r) FROM Review r WHERE r.doctor.doctorId = :doctorId), " +
            "d.averageRating = COALESCE((SELECT AVG(r.rating) FROM Review r WHERE r.doctor.doctorId = :doctorId), 0.0) " +
            "WHERE d.doctorId = :doctorId")
    int recomputeRatingStats(@Param("doctorId") String doctorId);

    /**
     * Direct update - avoids a {@code CascadeType.ALL} merge on appointments/slots.
     *
     * <p>{@code clearAutomatically} and {@code flushAutomatically} are required, not decoration:
     * this statement writes around the persistence context, so any {@code Doctor} already loaded in
     * the session would otherwise keep its pre-update field values and be handed straight back to
     * the caller.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Doctor d SET " +
            "d.firstName    = CASE WHEN :name IS NOT NULL THEN :name ELSE d.firstName END, " +
            "d.qualification = CASE WHEN :qualification IS NOT NULL THEN :qualification ELSE d.qualification END, " +
            "d.experienceYears = CASE WHEN :experienceYears IS NOT NULL THEN :experienceYears ELSE d.experienceYears END, " +
            "d.consultationFee = CASE WHEN :consultationFee IS NOT NULL THEN :consultationFee ELSE d.consultationFee END, " +
            "d.about = CASE WHEN :about IS NOT NULL THEN :about ELSE d.about END " +
            "WHERE d.doctorId = :doctorId")
    int updateProfileFields(
            @Param("doctorId") String doctorId,
            @Param("name") String name,
            @Param("qualification") String qualification,
            @Param("experienceYears") Integer experienceYears,
            @Param("consultationFee") BigDecimal consultationFee,
            @Param("about") String about
    );
}
