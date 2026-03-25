package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Specialization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, String> {

    Optional<Doctor> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCountryCodeAndPhoneNumber(String countryCode, String phoneNumber);

    // ✅ Find by exact specialization
    List<Doctor> findBySpecialization(Specialization specialization);

    // ✅ Find by specialization and active status
    List<Doctor> findBySpecializationAndIsActiveTrue(Specialization specialization);

    // ✅ Find all active doctors
    List<Doctor> findByIsActiveTrue();

    // ✅ Find by multiple specializations
    List<Doctor> findBySpecializationIn(List<Specialization> specializations);

    // ✅ Search doctors by name
    List<Doctor> findByFirstNameContainingIgnoreCaseAndIsActiveTrueOrLastNameContainingIgnoreCaseAndIsActiveTrue(String firstName, String lastName);

    // ✅ Custom query - Find doctors with available slots
    @Query("SELECT DISTINCT d FROM Doctor d " +
            "JOIN d.availabilitySlots s " +
            "WHERE d.specialization = :specialization " +
            "AND s.isAvailable = true " +
            "AND d.isActive = true")
    List<Doctor> findAvailableDoctorsBySpecialization(Specialization specialization);

    // ✅ Direct update query — avoids CascadeType.ALL merge on appointments/slots
    @Modifying
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
