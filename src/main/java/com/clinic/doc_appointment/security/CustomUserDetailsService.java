package com.clinic.doc_appointment.security;

import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.entity.Patient;
import com.clinic.doc_appointment.enums.Role;
import com.clinic.doc_appointment.repository.DoctorRepository;
import com.clinic.doc_appointment.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Check doctors first
        Optional<Doctor> doctor = doctorRepository.findByEmail(email);
        if (doctor.isPresent()) {
            Doctor d = doctor.get();
            return new UserPrincipal(d.getDoctorId(), d.getEmail(), d.getPassword(), Role.DOCTOR.authority());
        }

        // Then check patients
        Optional<Patient> patient = patientRepository.findByEmail(email);
        if (patient.isPresent()) {
            Patient p = patient.get();
            return new UserPrincipal(p.getPatientId(), p.getEmail(), p.getPassword(), Role.PATIENT.authority());
        }

        throw new UsernameNotFoundException("User not found with email: " + email);
    }
}
