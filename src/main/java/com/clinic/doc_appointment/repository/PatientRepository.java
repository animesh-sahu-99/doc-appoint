// repository/PatientRepository.java
package com.clinic.doc_appointment.repository;

import com.clinic.doc_appointment.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, String> {

    Optional<Patient> findByEmail(String email);

    Optional<Patient> findByCountryCodeAndPhoneNumber(String countryCode, String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByCountryCodeAndPhoneNumber(String countryCode, String phoneNumber);
}