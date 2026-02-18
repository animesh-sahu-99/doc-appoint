package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Specialization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
