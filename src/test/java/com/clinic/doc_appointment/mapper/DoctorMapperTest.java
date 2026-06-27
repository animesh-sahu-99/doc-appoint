package com.clinic.doc_appointment.mapper;

import com.clinic.doc_appointment.dto.response.DoctorResponse;
import com.clinic.doc_appointment.entity.Doctor;
import com.clinic.doc_appointment.enums.Specialization;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoctorMapperTest {

    private final DoctorMapper mapper = new DoctorMapper();

    @Test
    void mapsAllFieldsAndComposesFullName() {
        Doctor doctor = new Doctor()
                .setFirstName("Anjali")
                .setLastName("Sharma")
                .setEmail("anjali@clinic.com")
                .setPhoneNumber("9415050850")
                .setSpecialization(Specialization.CARDIOLOGIST)
                .setQualification("MD")
                .setExperienceYears(10)
                .setConsultationFee(new BigDecimal("500"))
                .setAbout("Heart specialist")
                .setIsActive(true);

        DoctorResponse r = mapper.toResponse(doctor);

        assertEquals("Anjali Sharma", r.getName());
        assertEquals("anjali@clinic.com", r.getEmail());
        assertEquals("9415050850", r.getPhone());
        assertEquals(Specialization.CARDIOLOGIST, r.getSpecialization());
        assertEquals(Specialization.CARDIOLOGIST.getDisplayName(), r.getSpecializationDisplayName());
        assertEquals(Specialization.CARDIOLOGIST.getDescription(), r.getSpecializationDescription());
        assertEquals("MD", r.getQualification());
        assertEquals(10, r.getExperienceYears());
        assertEquals(0, r.getConsultationFee().compareTo(new BigDecimal("500")));
        assertTrue(r.getIsActive());
    }

    @Test
    void omitsNullLastNameInComposedName() {
        Doctor doctor = new Doctor()
                .setFirstName("House")
                .setSpecialization(Specialization.CARDIOLOGIST)
                .setIsActive(true);

        assertEquals("House", mapper.toResponse(doctor).getName());
    }
}
